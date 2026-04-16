// Package auth verifies WordFlow app JWTs or Clerk-issued JWTs and injects
// the authenticated Clerk user id into the request context.
//
// Why Clerk and not something else?
//
//   - Clerk runs its own identity service, mints JWTs signed with RS256,
//     and exposes a JWKS endpoint. All we do is verify the token signature
//     against the public key set, check expiry/nbf/authorized party, and
//     trust the `sub` claim as the user id. No password handling, no
//     session storage, no email-verification flow on our side.
//
//   - clerk-sdk-go/v2's jwt.Verify handles JWKS fetching and caching for
//     us. It pulls the JWKS lazily on first verify and refreshes when
//     it sees an unknown `kid` (indicating key rotation).
//
// Traps this file intentionally defends against:
//
//   - Clerk uses `azp` (authorized party) rather than the standard `aud`
//     (audience) claim. If we don't tell the verifier what azp to
//     accept, it'll happily verify tokens minted for any Clerk instance.
//     AuthorizedParty in the config is the fix.
//
//   - Clock skew between our container and Clerk's servers would reject
//     freshly-issued tokens. 60s leeway is standard.
//
//   - A middleware that calls Clerk's REST API on every request would be
//     slow and create a hard runtime dependency on Clerk's uptime. We
//     only verify signatures locally against the cached JWKS.
package auth

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"strings"
	"time"

	"github.com/clerk/clerk-sdk-go/v2"
	"github.com/clerk/clerk-sdk-go/v2/jwt"
)

// Config holds the runtime settings the middleware needs. Populated from
// the top-level envconfig struct in internal/config.
type Config struct {
	// SecretKey is Clerk's backend API key (starts with sk_...). Not used
	// for JWT verification — that's done against the public JWKS —
	// but clerk-sdk-go uses it to authenticate when fetching instance
	// metadata (including the JWKS URL for the right instance).
	SecretKey string

	// AuthorizedParty is the `azp` claim we'll accept on incoming JWTs.
	// Matches the `Frontend API` domain of your Clerk instance (e.g.
	// "https://some-app.clerk.accounts.dev" or your custom domain).
	// If empty, the middleware refuses to boot — see Init.
	AuthorizedParty string

	// AppJWTSecret signs WordFlow-issued app tokens. If empty, Init
	// falls back to SecretKey so existing deployments keep working; set
	// APP_JWT_SECRET in production to rotate app tokens independently
	// from Clerk credentials.
	AppJWTSecret string
}

// contextKey is an unexported type so external packages can't accidentally
// collide with our context keys. Standard Go pattern for request-scoped
// values.
type contextKey int

const userIDKey contextKey = iota

// ErrUnauthorized is returned by verifyAndExtract when a request should
// be rejected with 401. Callers don't inspect the specific cause — the
// middleware just writes a generic error body and moves on.
var ErrUnauthorized = errors.New("unauthorized")

// Init configures the Clerk SDK with the given secret key. Must be
// called once at startup before any request hits the middleware.
//
// Behavior based on what's set in cfg:
//
//   - SecretKey empty: protected routes reject every request with 401
//     (the middleware short-circuits before touching the SDK). Dev-only
//     posture for pre-Clerk setup.
//
//   - SecretKey set, AuthorizedParty empty: full JWT verification
//     against Clerk's JWKS happens, but the `azp` claim is NOT checked.
//     Logs a warning because this means a valid token from a DIFFERENT
//     Clerk instance would be accepted. Acceptable for local dev;
//     must be fixed before Railway.
//
//   - Both set: full verification including azp match. Production mode.
func Init(cfg Config, logger *slog.Logger) error {
	appSecret := cfg.AppJWTSecret
	if appSecret == "" {
		appSecret = cfg.SecretKey
	}
	ConfigureAppJWT(appSecret)
	if cfg.AppJWTSecret == "" && cfg.SecretKey != "" {
		logger.Warn("APP_JWT_SECRET not set; using CLERK_SECRET_KEY for WordFlow app tokens")
	} else if cfg.AppJWTSecret != "" {
		logger.Info("wordflow app jwt initialized")
	}

	if cfg.SecretKey == "" {
		if AppJWTConfigured() {
			logger.Warn("CLERK_SECRET_KEY not set; only WordFlow app JWTs will be accepted")
		} else {
			logger.Warn("CLERK_SECRET_KEY not set; protected routes will reject all requests")
		}
		return nil
	}
	clerk.SetKey(cfg.SecretKey)

	if cfg.AuthorizedParty == "" {
		logger.Warn("CLERK_AUTHORIZED_PARTY not set; azp claim will not be validated (dev only — set before production)")
	} else {
		logger.Info("clerk auth initialized", "authorized_party", cfg.AuthorizedParty)
	}
	return nil
}

// Middleware returns a chi-compatible http middleware that:
//   - Reads the Authorization: Bearer ... header
//   - Verifies the JWT against Clerk's JWKS and the configured AuthorizedParty
//   - Extracts the Subject claim (the Clerk user id, e.g. "user_2abc...")
//   - Stashes it in the request context under userIDKey
//   - Writes 401 and bails if any of the above fails
//
// The middleware is returned as a closure over cfg + logger so main.go
// can wire it up with a single call. The actual verification happens
// inline in the wrapping handler.
//
// If SecretKey isn't set, the middleware short-circuits to 401 before
// touching the Clerk SDK — a clearer error path than letting jwt.Verify
// explode on a missing API key.
//
// AuthorizedParty enforcement is only wired in when it's configured;
// otherwise the azp claim is accepted as-is. See Init for the rationale.
func Middleware(cfg Config, logger *slog.Logger) func(next http.Handler) http.Handler {
	ready := cfg.SecretKey != "" || AppJWTConfigured()

	// Decide once at construction time whether to pass an
	// AuthorizedPartyHandler to jwt.Verify. nil means "don't check".
	var azpHandler func(string) bool
	if cfg.AuthorizedParty != "" {
		want := cfg.AuthorizedParty
		azpHandler = func(azp string) bool { return azp == want }
	}

	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if !ready {
				writeUnauthorized(w, logger, "clerk not configured on server")
				return
			}

			token, ok := extractBearerToken(r.Header.Get("Authorization"))
			if !ok {
				writeUnauthorized(w, logger, "missing or malformed Authorization header")
				return
			}

			if subject, handled, err := VerifyAppToken(token, 60*time.Second); handled {
				if err != nil {
					writeUnauthorized(w, logger, "app jwt verify failed: "+err.Error())
					return
				}
				ctx := context.WithValue(r.Context(), userIDKey, subject)
				next.ServeHTTP(w, r.WithContext(ctx))
				return
			}

			if cfg.SecretKey == "" {
				writeUnauthorized(w, logger, "clerk not configured on server")
				return
			}

			claims, err := jwt.Verify(r.Context(), &jwt.VerifyParams{
				Token:                  token,
				AuthorizedPartyHandler: azpHandler,
				Leeway:                 60 * time.Second,
			})
			if err != nil {
				writeUnauthorized(w, logger, "jwt verify failed: "+err.Error())
				return
			}

			// Subject is the Clerk user id — what we use everywhere in
			// the db layer as the scoping column.
			ctx := context.WithValue(r.Context(), userIDKey, claims.Subject)
			next.ServeHTTP(w, r.WithContext(ctx))
		})
	}
}

// UserIDFromCtx returns the authenticated Clerk user id stashed by
// Middleware. The second return is false when the key isn't set, which
// should only happen if a handler is mounted outside the auth middleware
// group — a programming bug, not a runtime condition.
func UserIDFromCtx(ctx context.Context) (string, bool) {
	v, ok := ctx.Value(userIDKey).(string)
	return v, ok
}

// MustUserIDFromCtx is the panicking variant. Use it inside handlers
// mounted under the auth middleware where the userID is guaranteed.
// Panics become 500s thanks to chi's Recoverer middleware, which is
// the right outcome for a routing misconfiguration.
func MustUserIDFromCtx(ctx context.Context) string {
	v, ok := UserIDFromCtx(ctx)
	if !ok {
		panic("auth.MustUserIDFromCtx: no user id in context (middleware missing?)")
	}
	return v
}

// extractBearerToken parses "Bearer <token>" out of an Authorization
// header value. Case-insensitive scheme match; empty token → not ok.
func extractBearerToken(header string) (string, bool) {
	const scheme = "Bearer "
	if len(header) <= len(scheme) {
		return "", false
	}
	if !strings.EqualFold(header[:len(scheme)], scheme) {
		return "", false
	}
	token := strings.TrimSpace(header[len(scheme):])
	if token == "" {
		return "", false
	}
	return token, true
}

// writeUnauthorized renders a consistent 401 body and logs the reason at
// Warn level (not Error — 401s are expected in normal operation).
// The public response intentionally hides the specific reason so an
// attacker can't use it as an oracle.
func writeUnauthorized(w http.ResponseWriter, logger *slog.Logger, reason string) {
	logger.Warn("auth rejected", "reason", reason)
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusUnauthorized)
	_, _ = w.Write([]byte(`{"error":"unauthorized"}`))
}
