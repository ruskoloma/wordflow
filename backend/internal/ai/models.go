package ai

import "strings"

const DefaultModel = "google/gemini-2.5-flash-lite"

var allowedModels = map[string]struct{}{
	"google/gemini-2.5-flash-lite":   {},
	"openai/gpt-4.1-mini":            {},
	"openai/gpt-4o-mini":             {},
	"anthropic/claude-haiku-4.5":     {},
	"deepseek/deepseek-chat-v3-0324": {},
}

func NormalizeModelID(model string) string {
	return strings.TrimSpace(model)
}

func IsAllowedModel(model string) bool {
	_, ok := allowedModels[NormalizeModelID(model)]
	return ok
}
