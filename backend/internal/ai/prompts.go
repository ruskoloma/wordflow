package ai

import (
	"fmt"
	"strings"
)

// TranslateSystemPrompt is the instruction we give the model for a
// single-word translation. Ported verbatim from the Android client's
// OpenRouterService.kt so the response shape stays backwards-compatible
// with any code that still parses the raw OpenRouter output.
const TranslateSystemPrompt = `You are a vocabulary assistant. Translate English words/phrases to Russian.
Return ONLY valid JSON with these fields:
- "translation": Russian translation (string, required)
- "examples": 1-2 English usage examples (array of strings)
- "pronunciation": approximate Russian pronunciation guide using English letters (string)
- "reason": brief explanation of the word's meaning or origin (string)
- "suggestion": if the input looks like a typo, suggest the correct word (string, empty if no typo)
- "suggestionTranslation": Russian translation of the suggestion (string, empty if no suggestion)
- "matchedCollections": which of the user's existing collections this word fits (array of strings)
- "suggestedCollections": suggest 1-2 NEW collection names this word could belong to (array of strings)
- "difficulty": difficulty score 1-10, where 1=very common, 10=very rare (integer)

Important: Return ONLY the JSON object, no markdown, no explanation.`

// TranslateUserPrompt builds the user-side prompt from the word and the
// list of existing collection names so the model can suggest matches.
func TranslateUserPrompt(word string, existingCollections []string) string {
	collStr := "none yet"
	if len(existingCollections) > 0 {
		collStr = strings.Join(existingCollections, ", ")
	}
	return fmt.Sprintf(`Translate this English word/phrase to Russian: %q

User's existing collections: %s`, word, collStr)
}

// GenerateCollectionSystemPrompt is the system prompt for batch
// generation of themed vocabulary lists.
const GenerateCollectionSystemPrompt = `You are a vocabulary assistant that generates English word lists for learning.
Return ONLY a valid JSON object with a single field "words" containing an array.
Each item in the array must have these fields:
- "word": the English word or short phrase (string)
- "translation": Russian translation (string)
- "examples": 1-2 English usage examples (array of strings)
- "pronunciation": approximate Russian pronunciation using English letters (string)
- "reason": brief explanation of meaning (string)
- "difficulty": difficulty score 1-10 (integer)

Return ONLY the JSON object, no markdown.`

// GenerateCollectionUserPrompt builds the user prompt for
// generate-collection. targetDifficulty is a 1-10 hint; the model
// usually respects it.
func GenerateCollectionUserPrompt(name, description string, count, targetDifficulty int) string {
	var label string
	switch {
	case targetDifficulty <= 3:
		label = "easy, common everyday words"
	case targetDifficulty <= 6:
		label = "intermediate, moderately challenging words"
	case targetDifficulty <= 8:
		label = "advanced, less common words"
	default:
		label = "expert-level, rare and sophisticated words"
	}

	var sb strings.Builder
	fmt.Fprintf(&sb, `Generate exactly %d English words/phrases for a vocabulary collection called %q.`, count, name)
	if description != "" {
		sb.WriteString("\nDescription: ")
		sb.WriteString(description)
	}
	fmt.Fprintf(&sb, `
Target difficulty: approximately %d out of 10 (%s).
Pick diverse, useful words that fit this topic and match the target difficulty level.
Order from easiest to hardest.`, targetDifficulty, label)

	return sb.String()
}
