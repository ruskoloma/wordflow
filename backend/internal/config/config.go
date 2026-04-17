// Package config loads and validates the server's environment configuration.
//
// We use envconfig instead of raw os.Getenv so that:
//   - Required fields fail-fast at startup with a clear message
//   - Defaults are declared alongside the field, not scattered in main
//   - Types are parsed (ints, durations, bools) rather than string-compared
//
// If any `required` env var is missing, Load returns an error describing
// exactly which one — so the container either boots clean or explodes
// loudly, never in some degraded "it runs but nothing works" middle state.
package config

import (
	"github.com/kelseyhightower/envconfig"
)

// Config is everything the server needs from the environment.
// Populated via envconfig.Process("", &cfg) which looks at the struct tags.
type Config struct {
	// HTTP
	Port string `envconfig:"PORT" default:"8080"`
	Env  string `envconfig:"ENV"  default:"development"`

	DatabaseURL string `envconfig:"DATABASE_URL" required:"true"`

	ClerkSecretKey       string `envconfig:"CLERK_SECRET_KEY"`
	ClerkAuthorizedParty string `envconfig:"CLERK_AUTHORIZED_PARTY"`

	// WordFlow app JWTs are issued after Clerk verifies the email-code
	// login. If unset, the server falls back to CLERK_SECRET_KEY.
	AppJWTSecret string `envconfig:"APP_JWT_SECRET"`

	// Publishable key (pk_test_... or pk_live_...). Required for the
	// passwordless email-code sign-in flow — we talk to Clerk's
	// Frontend API on the client's behalf and that API authenticates
	// requests with the publishable key (not the secret key).
	// The Frontend API host is derived from the key's base64 suffix.
	ClerkPublishableKey string `envconfig:"CLERK_PUBLISHABLE_KEY"`

	OpenrouterAPIKey string `envconfig:"OPENROUTER_API_KEY"`
	OpenrouterModel  string `envconfig:"OPENROUTER_MODEL" default:"google/gemini-2.5-flash-lite"`
}

// Load reads the environment into a Config. Called once from main.
// Returns a wrapped error if a required var is missing or unparseable.
func Load() (Config, error) {
	var cfg Config
	if err := envconfig.Process("", &cfg); err != nil {
		return cfg, err
	}
	return cfg, nil
}
