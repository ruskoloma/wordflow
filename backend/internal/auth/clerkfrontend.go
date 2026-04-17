package auth

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"
)

// Package auth's Clerk **Frontend** API client.
//
// The rest of the Clerk integration uses the backend SDK (sk_test_...,
// clerk-sdk-go/v2). This file wraps the user-facing Frontend API
// (pk_test_..., smiling-bedbug-57.clerk.accounts.dev/v1/client/...)
// because the passwordless email-code sign-in flow lives there —
// specifically the /v1/client/sign_ins/{id}/{prepare,attempt}_first_factor
// endpoints that drive the "enter email → get OTP → enter code" UX.
//
// The two interesting details:
//
//   1. Clerk Frontend API has a "client" state machine: the first call
//      issues a "dev browser JWT" in the response's Authorization
//      header, and every subsequent call in the same sign-in attempt
//      must send that JWT back as Authorization: Bearer. This
//      replaces the cookie-based state that web SDKs use; native SDKs
//      pass the dev_browser_jwt around explicitly. We do the same
//      server-side via ClerkFrontendSession.
//
//   2. The Frontend API host is encoded in the publishable key: the
//      base64 body after pk_test_ / pk_live_ decodes to the instance's
//      DNS name (e.g. "smiling-bedbug-57.clerk.accounts.dev$").

// ClerkFrontend is the HTTP client. One instance per process is fine —
// there's no per-request state on the struct itself; all that lives on
// ClerkFrontendSession values.
type ClerkFrontend struct {
	http      *http.Client
	baseURL   string // https://smiling-bedbug-57.clerk.accounts.dev
	publicKey string // pk_test_... or pk_live_...
}

// ClerkFrontendSession carries the per-sign-in state between calls:
// the dev browser JWT (replaces the publishable key as Authorization
// on subsequent requests) and the sign_in_id the flow is operating on.
type ClerkFrontendSession struct {
	DevBrowserJWT  string
	SignInID       string
	EmailAddressID string
}

// NewClerkFrontend builds a client. publishableKey must be the pk_...
// key; frontendAPIURL is optional and, if empty, is derived from the
// key's base64 body.
func NewClerkFrontend(publishableKey, frontendAPIURL string) (*ClerkFrontend, error) {
	if publishableKey == "" {
		return nil, errors.New("clerk publishable key required")
	}
	base := strings.TrimRight(frontendAPIURL, "/")
	if base == "" {
		derived, err := deriveFrontendAPIURL(publishableKey)
		if err != nil {
			return nil, err
		}
		base = derived
	}
	return &ClerkFrontend{
		http:      &http.Client{Timeout: 20 * time.Second},
		baseURL:   base,
		publicKey: publishableKey,
	}, nil
}

// BaseURL returns the Frontend API host used by this client (diagnostic).
func (c *ClerkFrontend) BaseURL() string { return c.baseURL }

// ------------------------------------------------------------------
// Flow: passwordless sign-in by email code
// ------------------------------------------------------------------

// StartSignIn submits the email to Clerk and prepares the email_code
// first factor. Returns a populated session the caller can persist
// across the HTTP request boundary.
//
// Clerk behavior worth knowing:
//
//   - If the identifier doesn't match an existing Clerk user, the
//     response has an error with code "form_identifier_not_found".
//     The caller (auth_email.go) handles that by creating the user
//     via the backend SDK first, then retrying.
//
//   - For emails containing "+clerk_test", Clerk auto-accepts the
//     magic code "424242" on attempt_first_factor without actually
//     sending anything. For real emails, a real OTP is delivered
//     through Clerk's own email provider.
func (c *ClerkFrontend) StartSignIn(ctx context.Context, email string) (*ClerkFrontendSession, error) {
	// Step 1: create the sign-in attempt. Authenticated with the
	// publishable key because we don't have a dev_browser_jwt yet.
	body := url.Values{}
	body.Set("identifier", email)
	si, dbjwt, err := c.formPost(ctx, "/v1/client/sign_ins", c.publicKey, body)
	if err != nil {
		return nil, err
	}
	siResp, err := parseSignIn(si)
	if err != nil {
		return nil, err
	}
	// Pick the email_code factor and its email_address_id.
	emailID := findEmailCodeFactorID(siResp.SupportedFirstFactors)
	if emailID == "" {
		return nil, fmt.Errorf("clerk: email_code is not a supported first factor for this user")
	}

	// Step 2: prepare the email-code verification. Now authenticated
	// with the dev_browser_jwt from step 1.
	prepBody := url.Values{}
	prepBody.Set("strategy", "email_code")
	prepBody.Set("email_address_id", emailID)
	_, _, err = c.formPost(ctx,
		"/v1/client/sign_ins/"+siResp.ID+"/prepare_first_factor",
		dbjwt, prepBody)
	if err != nil {
		return nil, err
	}

	return &ClerkFrontendSession{
		DevBrowserJWT:  dbjwt,
		SignInID:       siResp.ID,
		EmailAddressID: emailID,
	}, nil
}

// AttemptSignIn submits the user-provided code and returns Clerk's
// freshly-created session id when the code is accepted.
func (c *ClerkFrontend) AttemptSignIn(ctx context.Context, session *ClerkFrontendSession, code string) (sessionID string, err error) {
	body := url.Values{}
	body.Set("strategy", "email_code")
	body.Set("code", code)
	raw, _, err := c.formPost(ctx,
		"/v1/client/sign_ins/"+session.SignInID+"/attempt_first_factor",
		session.DevBrowserJWT, body)
	if err != nil {
		return "", err
	}
	resp, err := parseSignIn(raw)
	if err != nil {
		return "", err
	}
	if resp.Status != "complete" || resp.CreatedSessionID == "" {
		return "", fmt.Errorf("clerk sign-in incomplete (status=%q)", resp.Status)
	}
	return resp.CreatedSessionID, nil
}

// ------------------------------------------------------------------
// HTTP transport
// ------------------------------------------------------------------

// formPost issues a Clerk Frontend API call with application/x-www-
// form-urlencoded body. Returns:
//   - the raw response body (for the caller to parse)
//   - the dev_browser_jwt to use on the NEXT call in this flow; same
//     as the one sent in if the server didn't rotate it, or a fresh
//     value if the Authorization response header was populated
//   - an error if the status wasn't 2xx
//
// auth is either a publishable key (first call only) or a
// dev_browser_jwt from a prior response.
func (c *ClerkFrontend) formPost(ctx context.Context, path, auth string, form url.Values) ([]byte, string, error) {
	// _is_native=1 tells Clerk to return the dev_browser_jwt in the
	// Authorization response header instead of setting cookies, which
	// is the posture native apps use — and what our server-side proxy
	// effectively emulates.
	u := c.baseURL + path + "?_clerk_js_version=5&_is_native=1"

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, u, strings.NewReader(form.Encode()))
	if err != nil {
		return nil, auth, err
	}
	req.Header.Set("Authorization", "Bearer "+bearerToken(auth))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")

	resp, err := c.http.Do(req)
	if err != nil {
		return nil, auth, fmt.Errorf("clerk request: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	if err != nil {
		return nil, auth, fmt.Errorf("clerk read body: %w", err)
	}

	// Clerk rotates / issues the dev_browser_jwt via the Authorization
	// response header. Use the new one if present.
	newAuth := auth
	if h := resp.Header.Get("Authorization"); h != "" {
		newAuth = bearerToken(h)
	}

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return body, newAuth, &ClerkAPIError{
			Status: resp.StatusCode,
			Body:   string(body),
		}
	}
	return body, newAuth, nil
}

// ClerkAPIError surfaces non-2xx responses from the Frontend API with
// the raw body intact so callers can extract Clerk's structured errors
// (error codes, long messages, etc.).
type ClerkAPIError struct {
	Status int
	Body   string
}

func (e *ClerkAPIError) Error() string {
	snippet := e.Body
	if len(snippet) > 300 {
		snippet = snippet[:300] + "...(truncated)"
	}
	return fmt.Sprintf("clerk frontend api %d: %s", e.Status, snippet)
}

// HasErrorCode walks Clerk's {"errors":[{"code":...}]} envelope and
// reports whether any error carries the given machine-readable code.
// Used by auth_email.go to distinguish "user not found" from a real
// failure.
func (e *ClerkAPIError) HasErrorCode(code string) bool {
	var envelope struct {
		Errors []struct {
			Code string `json:"code"`
		} `json:"errors"`
	}
	if err := json.Unmarshal([]byte(e.Body), &envelope); err != nil {
		return false
	}
	for _, x := range envelope.Errors {
		if x.Code == code {
			return true
		}
	}
	return false
}

// ------------------------------------------------------------------
// Response parsing
// ------------------------------------------------------------------

// clerkSignInResponse is a trimmed view of the sign_in_attempt object
// returned by the Frontend API. We only pull the fields this flow
// needs; everything else is ignored.
type clerkSignInResponse struct {
	ID                    string
	Status                string
	CreatedSessionID      string
	SupportedFirstFactors []clerkFirstFactor
}

type clerkFirstFactor struct {
	Strategy       string
	EmailAddressID string
}

func parseSignIn(body []byte) (*clerkSignInResponse, error) {
	// The Frontend API wraps every payload in {"response": ..., "client": ...}.
	// We only care about .response here.
	var envelope struct {
		Response struct {
			ID                    string `json:"id"`
			Status                string `json:"status"`
			CreatedSessionID      string `json:"created_session_id"`
			SupportedFirstFactors []struct {
				Strategy       string `json:"strategy"`
				EmailAddressID string `json:"email_address_id"`
			} `json:"supported_first_factors"`
		} `json:"response"`
	}
	if err := json.Unmarshal(body, &envelope); err != nil {
		return nil, fmt.Errorf("clerk response parse: %w", err)
	}
	out := &clerkSignInResponse{
		ID:               envelope.Response.ID,
		Status:           envelope.Response.Status,
		CreatedSessionID: envelope.Response.CreatedSessionID,
	}
	for _, f := range envelope.Response.SupportedFirstFactors {
		out.SupportedFirstFactors = append(out.SupportedFirstFactors, clerkFirstFactor{
			Strategy:       f.Strategy,
			EmailAddressID: f.EmailAddressID,
		})
	}
	return out, nil
}

func findEmailCodeFactorID(factors []clerkFirstFactor) string {
	for _, f := range factors {
		if f.Strategy == "email_code" {
			return f.EmailAddressID
		}
	}
	return ""
}

func bearerToken(value string) string {
	value = strings.TrimSpace(value)
	if len(value) > len("Bearer ") && strings.EqualFold(value[:len("Bearer ")], "Bearer ") {
		return strings.TrimSpace(value[len("Bearer "):])
	}
	return value
}

// ------------------------------------------------------------------
// Publishable key → Frontend API URL
// ------------------------------------------------------------------

// deriveFrontendAPIURL pulls the instance DNS name out of a Clerk
// publishable key. The key shape is:
//
//	pk_test_<base64("smiling-bedbug-57.clerk.accounts.dev$")>
//	pk_live_<base64("clerk.yourdomain.com$")>
//
// We strip the leading pk_test_/pk_live_, base64-decode, trim the
// trailing "$", and slap https:// on the front.
func deriveFrontendAPIURL(publishableKey string) (string, error) {
	const (
		testPrefix = "pk_test_"
		livePrefix = "pk_live_"
	)
	rest := ""
	switch {
	case strings.HasPrefix(publishableKey, testPrefix):
		rest = publishableKey[len(testPrefix):]
	case strings.HasPrefix(publishableKey, livePrefix):
		rest = publishableKey[len(livePrefix):]
	default:
		return "", fmt.Errorf("clerk publishable key has unknown prefix (expected pk_test_ or pk_live_)")
	}
	// Clerk publishable keys encode the host with unpadded base64.
	// Try raw (no padding) first; if the caller ever sends a key with
	// padding we fall back to the standard encoding.
	raw, err := base64.RawStdEncoding.DecodeString(rest)
	if err != nil {
		raw, err = base64.StdEncoding.DecodeString(rest)
	}
	if err != nil {
		return "", fmt.Errorf("clerk publishable key base64 decode: %w", err)
	}
	host := strings.TrimRight(string(raw), "$")
	if host == "" {
		return "", fmt.Errorf("clerk publishable key decoded to empty host")
	}
	return "https://" + host, nil
}
