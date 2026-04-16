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

	// Database — required on every phase after Phase 1.
	DatabaseURL string `envconfig:"DATABASE_URL" required:"true"`

	// Clerk — optional until Phase 5 ships auth. The server should still
	// boot without these so we can iterate on DB stuff in isolation.
	ClerkSecretKey       string `envconfig:"CLERK_SECRET_KEY"`
	ClerkAuthorizedParty string `envconfig:"CLERK_AUTHORIZED_PARTY"`

	// Publishable key (pk_test_... or pk_live_...). Required for the
	// passwordless email-code sign-in flow — we talk to Clerk's
	// Frontend API on the client's behalf and that API authenticates
	// requests with the publishable key (not the secret key).
	// The Frontend API host is derived from the key's base64 suffix.
	ClerkPublishableKey string `envconfig:"CLERK_PUBLISHABLE_KEY"`

	// OpenRouter — optional until Phase 8 ships the AI proxy.
	// Model defaults to openai/gpt-4o-mini — cheap, fast, good
	// enough for single-word translation. Override via the
	// OPENROUTER_MODEL env var to swap providers without a code
	// change; the full list of candidates lives in
	// android/app/src/main/java/com/rsln/wordflow/data/remote/AiModels.kt.
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

// IsDevelopment returns true when ENV is "development" (the default).
// Useful later for conditionally enabling verbose logs or dev-only routes.
func (c Config) IsDevelopment() bool {
	return c.Env == "development"
}
