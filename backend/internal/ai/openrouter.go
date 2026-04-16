// Package ai is the server-side wrapper around OpenRouter's
// chat/completions API plus the prompts the WordFlow app uses.
//
// Why proxy through the backend instead of letting the Android app
// call OpenRouter directly:
//
//   - The API key lives in a Railway env var, not in the APK. Losing
//     the APK doesn't leak the key; rotating the key doesn't require
//     an app release.
//
//   - Rate limiting happens server-side (see ratelimit.go), so one
//     runaway client can't burn through the monthly budget.
//
//   - Prompts live in one place. Changing the system prompt doesn't
//     need a Play Store rollout.
//
//   - Model selection is server-driven. Swapping models (Gemini, GPT,
//     Claude) is a config change, not an app change.
package ai

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"time"
)

const defaultEndpoint = "https://openrouter.ai/api/v1/chat/completions"

// Client is a thin typed wrapper around OpenRouter's chat/completions
// endpoint. One instance per process, shared across handlers.
type Client struct {
	http   *http.Client
	apiKey string
	model  string
	// referer and title are OpenRouter-specific headers they use to
	// attribute traffic in their analytics dashboard. Worth setting
	// so our usage shows up under a human-readable name.
	referer  string
	title    string
	endpoint string
}

// Config bundles the settings needed to construct a Client.
type Config struct {
	APIKey  string
	Model   string        // e.g. "google/gemini-2.0-flash-001"
	Timeout time.Duration // total per-request timeout
}

// NewClient constructs a Client. If APIKey is empty, the client still
// constructs successfully but every ChatCompletion call returns an
// error — the handler catches this and returns 503 Service Unavailable
// so the server can run without an OpenRouter key in early dev.
func NewClient(cfg Config) *Client {
	timeout := cfg.Timeout
	if timeout == 0 {
		timeout = 45 * time.Second
	}
	return &Client{
		http:     &http.Client{Timeout: timeout},
		apiKey:   cfg.APIKey,
		model:    cfg.Model,
		referer:  "com.rsln.wordflow",
		title:    "WordFlow",
		endpoint: defaultEndpoint,
	}
}

// Configured reports whether the client has a usable API key. Handlers
// use it to short-circuit to a clear 503 before spending time building
// prompts.
func (c *Client) Configured() bool {
	return c.apiKey != ""
}

// Message is a single chat message in the OpenRouter request shape.
type Message struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

// ChatOptions tune the completion beyond the message list.
type ChatOptions struct {
	Model       string
	Temperature float64
	MaxTokens   int
	// JSONMode=true asks OpenRouter to set response_format=json_object,
	// which most modern models honor and return strict JSON for.
	// Combined with a system prompt that says "return JSON", this
	// eliminates most of the stray-markdown parsing headaches.
	JSONMode bool
}

// StatusError is returned when OpenRouter answers with a non-2xx.
// Handlers surface the status code to help debug auth / quota issues.
type StatusError struct {
	StatusCode int
	Body       string
}

func (e *StatusError) Error() string {
	// Truncate body so a multi-MB HTML 502 page doesn't fill the log.
	body := e.Body
	if len(body) > 500 {
		body = body[:500] + "...(truncated)"
	}
	return fmt.Sprintf("openrouter status %d: %s", e.StatusCode, body)
}

// ErrNotConfigured is returned when the client has no API key.
var ErrNotConfigured = errors.New("openrouter client not configured (OPENROUTER_API_KEY is empty)")

// ChatCompletion sends a chat request and returns the first choice's
// content string. The returned content is whatever the model chose to
// emit — typically a JSON object (in JSONMode) possibly wrapped in
// ``` fences. Callers should run it through StripJSONFences before
// unmarshalling.
func (c *Client) ChatCompletion(ctx context.Context, messages []Message, opts ChatOptions) (string, error) {
	if c.apiKey == "" {
		return "", ErrNotConfigured
	}

	model := NormalizeModelID(opts.Model)
	if model == "" {
		model = NormalizeModelID(c.model)
	}
	if model == "" {
		model = DefaultModel
	}

	body := map[string]any{
		"model":    model,
		"messages": messages,
	}
	if opts.Temperature > 0 {
		body["temperature"] = opts.Temperature
	}
	if opts.MaxTokens > 0 {
		body["max_tokens"] = opts.MaxTokens
	}
	if opts.JSONMode {
		body["response_format"] = map[string]string{"type": "json_object"}
	}

	bodyBytes, err := json.Marshal(body)
	if err != nil {
		return "", fmt.Errorf("marshal request: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, c.endpoint, bytes.NewReader(bodyBytes))
	if err != nil {
		return "", fmt.Errorf("new request: %w", err)
	}
	req.Header.Set("Authorization", "Bearer "+c.apiKey)
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("HTTP-Referer", c.referer)
	req.Header.Set("X-Title", c.title)

	resp, err := c.http.Do(req)
	if err != nil {
		return "", fmt.Errorf("openrouter request: %w", err)
	}
	defer resp.Body.Close()

	respBytes, err := io.ReadAll(io.LimitReader(resp.Body, 1<<20)) // 1 MB cap
	if err != nil {
		return "", fmt.Errorf("read response: %w", err)
	}

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return "", &StatusError{
			StatusCode: resp.StatusCode,
			Body:       string(respBytes),
		}
	}

	var parsed struct {
		Choices []struct {
			Message struct {
				Content string `json:"content"`
			} `json:"message"`
		} `json:"choices"`
	}
	if err := json.Unmarshal(respBytes, &parsed); err != nil {
		return "", fmt.Errorf("parse openrouter envelope: %w", err)
	}
	if len(parsed.Choices) == 0 || parsed.Choices[0].Message.Content == "" {
		return "", errors.New("openrouter: empty response content")
	}

	return parsed.Choices[0].Message.Content, nil
}

// StripJSONFences removes leading/trailing ``` and ```json markers
// that some models wrap JSON responses in even when asked not to.
// Returns the content unchanged if no fences are present.
func StripJSONFences(s string) string {
	s = trimWS(s)
	s = trimPrefixFold(s, "```json")
	s = trimPrefixFold(s, "```")
	s = trimSuffix(s, "```")
	return trimWS(s)
}

// Small helpers kept local so this package has zero internal deps.
func trimWS(s string) string {
	for len(s) > 0 && (s[0] == ' ' || s[0] == '\n' || s[0] == '\r' || s[0] == '\t') {
		s = s[1:]
	}
	for len(s) > 0 && (s[len(s)-1] == ' ' || s[len(s)-1] == '\n' || s[len(s)-1] == '\r' || s[len(s)-1] == '\t') {
		s = s[:len(s)-1]
	}
	return s
}

func trimPrefixFold(s, prefix string) string {
	if len(s) < len(prefix) {
		return s
	}
	if equalFold(s[:len(prefix)], prefix) {
		return s[len(prefix):]
	}
	return s
}

func trimSuffix(s, suffix string) string {
	if len(s) < len(suffix) {
		return s
	}
	if s[len(s)-len(suffix):] == suffix {
		return s[:len(s)-len(suffix)]
	}
	return s
}

func equalFold(a, b string) bool {
	if len(a) != len(b) {
		return false
	}
	for i := 0; i < len(a); i++ {
		ca, cb := a[i], b[i]
		if ca >= 'A' && ca <= 'Z' {
			ca += 'a' - 'A'
		}
		if cb >= 'A' && cb <= 'Z' {
			cb += 'a' - 'A'
		}
		if ca != cb {
			return false
		}
	}
	return true
}
