package api

import "strings"

const (
	minDifficulty int16 = 1
	maxDifficulty int16 = 10
)

func trimRequiredString(value string) string {
	return strings.TrimSpace(value)
}

func normalizeWord(value string) string {
	return strings.ToLower(strings.TrimSpace(value))
}

func trimOptionalString(value *string) {
	if value != nil {
		*value = strings.TrimSpace(*value)
	}
}

func trimStringSlice(values []string) []string {
	out := values[:0]
	for _, value := range values {
		if trimmed := strings.TrimSpace(value); trimmed != "" {
			out = append(out, trimmed)
		}
	}
	return out
}

func validDifficulty(value int16) bool {
	return value >= minDifficulty && value <= maxDifficulty
}

func clampDifficulty(value int) int {
	if value < int(minDifficulty) {
		return int(minDifficulty)
	}
	if value > int(maxDifficulty) {
		return int(maxDifficulty)
	}
	return value
}
