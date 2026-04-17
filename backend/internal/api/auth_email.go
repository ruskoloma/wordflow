package api

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/clerk/clerk-sdk-go/v2/user"

	"github.com/rsln-ua/wordflow-backend/internal/auth"
)

// Passwordless email-code sign-in, flow:
//
//   1. Client POSTs /v1/auth/email/start {email}
//      Server:
//        a. Ensures a Clerk user exists for that email (creates one
//           via the backend SDK with skip_password_requirement if
//           necessary)
//        b. Drives Clerk Frontend API sign_ins → prepare_first_factor
//           (email_code), capturing the dev_browser_jwt + sign_in_id
//        c. Stashes that state in an in-memory map keyed by a random
//           state_id (10 min TTL)
//        d. Returns {state_id} to the client; Clerk has meanwhile
//           emailed the 6-digit code to the user (or, for
//           "+clerk_test" emails, auto-accepts the magic 424242)
//
//   2. Client POSTs /v1/auth/email/verify {state_id, code}
//      Server:
//        a. Looks up the stashed state
//        b. Calls Clerk Frontend API attempt_first_factor with the code
//        c. On success, mints a WordFlow app JWT for the Clerk user
//        d. Returns {jwt, user_id} to the client
//
// This avoids the client having to maintain the Clerk dev_browser_jwt
// or talk to the Frontend API directly. Android just sees two simple
// POSTs and stores the app JWT returned by the backend.

// authResponse is the success body for /v1/auth/email/verify. We
// return the email alongside the JWT so the client can show a
// human-readable "signed in as ..." without having to keep a side
// channel from the /email/start call or mint a Clerk JWT template
// with the email as a custom claim.
type authResponse struct {
	JWT    string `json:"jwt"`
	UserID string `json:"user_id"`
	Email  string `json:"email"`
}

// clerkConfigured is flipped on at startup (main.go → SetClerkConfigured)
// when CLERK_SECRET_KEY is non-empty. Used by the email auth handlers
// to return 503 before trying to call Clerk on an unconfigured server.
var clerkConfigured bool

// SetClerkConfigured is called from main.go once at startup.
func SetClerkConfigured(v bool) { clerkConfigured = v }

type emailStartRequest struct {
	Email string `json:"email"`
}

type emailStartResponse struct {
	StateID string `json:"state_id"`
	Email   string `json:"email"`
}

type emailVerifyRequest struct {
	StateID string `json:"state_id"`
	Code    string `json:"code"`
}

// --- in-memory state store --------------------------------------------

// stateEntry is what we stash between the /start and /verify calls.
// Kept small and opaque — the Android client sees only StateID.
type stateEntry struct {
	session   *auth.ClerkFrontendSession
	email     string
	expiresAt time.Time
}

// stateStore is a sync-safe map of state_id → stateEntry. Cleanup of
// expired entries happens lazily on Get (no background goroutine).
// Good enough for a dev instance that handles a handful of logins; if
// it ever matters we swap in a TTL map or Redis.
type stateStore struct {
	mu sync.Mutex
	m  map[string]stateEntry
}

func newStateStore() *stateStore { return &stateStore{m: make(map[string]stateEntry)} }

func (s *stateStore) put(id string, entry stateEntry) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.m[id] = entry
}

func (s *stateStore) take(id string) (stateEntry, bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	entry, ok := s.m[id]
	if !ok {
		return stateEntry{}, false
	}
	delete(s.m, id) // single-use — prevents replay
	if time.Now().After(entry.expiresAt) {
		return stateEntry{}, false
	}
	return entry, true
}

// --- package-level singletons set from main.go ------------------------

var (
	emailAuthStates   = newStateStore()
	clerkFrontend     *auth.ClerkFrontend // nil until SetClerkFrontend runs
	clerkFrontendLock sync.RWMutex
)

// SetClerkFrontend is called from main.go after we construct the
// Clerk Frontend client. Stored via a setter rather than a
// constructor arg so we don't have to plumb another dependency
// through the Deps struct — the auth handlers look it up at request
// time and return a clean 503 if it's not configured.
func SetClerkFrontend(c *auth.ClerkFrontend) {
	clerkFrontendLock.Lock()
	defer clerkFrontendLock.Unlock()
	clerkFrontend = c
}

func currentClerkFrontend() *auth.ClerkFrontend {
	clerkFrontendLock.RLock()
	defer clerkFrontendLock.RUnlock()
	return clerkFrontend
}

// --- handlers ---------------------------------------------------------

// POST /v1/auth/email/start
func (h *handlers) authEmailStart(w http.ResponseWriter, r *http.Request) {
	cf := currentClerkFrontend()
	if !clerkConfigured || cf == nil {
		writeError(w, http.StatusServiceUnavailable, "clerk_not_configured",
			"CLERK_PUBLISHABLE_KEY is not set on the server")
		return
	}

	var body emailStartRequest
	if !decodeJSON(w, r, &body) {
		return
	}
	email := strings.TrimSpace(body.Email)
	if email == "" {
		writeError(w, http.StatusBadRequest, "missing_fields", "email is required")
		return
	}
	// Normalize to lowercase so "Alice@X.com" and "alice@x.com" map to
	// the same Clerk user. Clerk itself is case-insensitive on match
	// but it's nicer for our own lookups too.
	email = strings.ToLower(email)

	ctx, cancel := context.WithTimeout(r.Context(), 20*time.Second)
	defer cancel()

	// Ensure the user exists. Clerk Frontend API sign_ins returns
	// "form_identifier_not_found" if the identifier has never been
	// seen — we recover by creating the user via the backend SDK
	// (no password needed) and retrying.
	clerkSession, err := cf.StartSignIn(ctx, email)
	if err != nil {
		var apiErr *auth.ClerkAPIError
		if errors.As(err, &apiErr) && apiErr.HasErrorCode("form_identifier_not_found") {
			if createErr := createPasswordlessUser(ctx, email); createErr != nil {
				h.logger.Warn("clerk user.Create failed", "err", createErr, "email", email)
				writeError(w, http.StatusBadGateway, "user_create_failed",
					"could not create account")
				return
			}
			// Retry the sign-in — this time the user exists.
			clerkSession, err = cf.StartSignIn(ctx, email)
		}
	}
	if err != nil {
		h.logger.Warn("clerk email start failed", "err", err, "email", email)
		writeError(w, http.StatusBadGateway, "email_start_failed",
			"could not start sign-in; try again in a moment")
		return
	}

	stateID, err := newStateID()
	if err != nil {
		h.logger.Error("generate email auth state id", "err", err)
		writeError(w, http.StatusInternalServerError, "internal", "could not start sign-in")
		return
	}
	emailAuthStates.put(stateID, stateEntry{
		session:   clerkSession,
		email:     email,
		expiresAt: time.Now().Add(10 * time.Minute),
	})

	writeJSON(w, http.StatusOK, emailStartResponse{
		StateID: stateID,
		Email:   email,
	})
}

// POST /v1/auth/email/verify
func (h *handlers) authEmailVerify(w http.ResponseWriter, r *http.Request) {
	cf := currentClerkFrontend()
	if !clerkConfigured || cf == nil {
		writeError(w, http.StatusServiceUnavailable, "clerk_not_configured",
			"CLERK_PUBLISHABLE_KEY is not set on the server")
		return
	}

	var body emailVerifyRequest
	if !decodeJSON(w, r, &body) {
		return
	}
	body.Code = strings.TrimSpace(body.Code)
	if body.StateID == "" || body.Code == "" {
		writeError(w, http.StatusBadRequest, "missing_fields",
			"state_id and code are required")
		return
	}

	entry, ok := emailAuthStates.take(body.StateID)
	if !ok {
		writeError(w, http.StatusUnauthorized, "state_expired",
			"sign-in session expired — request a new code")
		return
	}

	ctx, cancel := context.WithTimeout(r.Context(), 20*time.Second)
	defer cancel()

	_, err := cf.AttemptSignIn(ctx, entry.session, body.Code)
	if err != nil {
		var apiErr *auth.ClerkAPIError
		if errors.As(err, &apiErr) {
			// Put the entry back so the user can retry the same
			// state with a new code, unless the underlying sign_in
			// attempt was invalidated (e.g. too many wrong codes).
			// Clerk's Frontend API returns "form_code_incorrect"
			// for a wrong code and keeps the sign_in open.
			if apiErr.HasErrorCode("form_code_incorrect") {
				entry.expiresAt = time.Now().Add(5 * time.Minute)
				emailAuthStates.put(body.StateID, entry)
				writeError(w, http.StatusUnauthorized, "invalid_code",
					"that code is incorrect")
				return
			}
		}
		h.logger.Warn("clerk email verify failed", "err", err)
		writeError(w, http.StatusBadGateway, "verify_failed",
			"could not verify code")
		return
	}

	// Look up the user id so the client can display it. The Clerk
	// session we just created is linked to the user, so an email
	// lookup is the simplest way back.
	users, listErr := user.List(ctx, &user.ListParams{
		EmailAddresses: []string{entry.email},
	})
	userID := ""
	if listErr == nil && users != nil && len(users.Users) > 0 {
		userID = users.Users[0].ID
	}
	if userID == "" {
		h.logger.Error("clerk user lookup after email verify failed", "err", listErr, "email", entry.email)
		writeError(w, http.StatusInternalServerError, "internal",
			"could not resolve signed-in user")
		return
	}

	token, err := auth.MintAppToken(userID, entry.email, 30*24*time.Hour)
	if err != nil {
		h.logger.Error("mint app token after email verify", "err", err)
		writeError(w, http.StatusInternalServerError, "internal",
			"could not mint session token")
		return
	}

	writeJSON(w, http.StatusOK, authResponse{
		JWT:    token,
		UserID: userID,
		Email:  entry.email,
	})
}

// --- helpers ----------------------------------------------------------

// createPasswordlessUser creates a Clerk user via the backend SDK with
// no password so the Frontend API sign-in flow can send an email code.
func createPasswordlessUser(ctx context.Context, email string) error {
	emails := []string{email}
	skipPassword := true
	_, err := user.Create(ctx, &user.CreateParams{
		EmailAddresses:          &emails,
		SkipPasswordRequirement: &skipPassword,
	})
	return err
}

// newStateID returns a random 32-char hex string.
func newStateID() (string, error) {
	var buf [16]byte
	if _, err := rand.Read(buf[:]); err != nil {
		return "", err
	}
	return hex.EncodeToString(buf[:]), nil
}
