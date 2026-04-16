package api

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"time"

	"github.com/rsln-ua/wordflow-backend/internal/ai"
	"github.com/rsln-ua/wordflow-backend/internal/auth"
)

// ---------- Request / response shapes -------------------------------
//
// The wire shapes here are *typed* versions of what the Android app
// used to build and send directly to OpenRouter. Moving the prompt
// engineering server-side means the client sends semantic inputs
// (word, collection name, difficulty) and gets back a cleaned-up
// result — no raw chat/completions bodies.
//
// Field names use snake_case to match the rest of the WordFlow API.
// The internal parsing intermediate still uses the camelCase keys
// that the model emits (see translateRaw / generatedWordRaw).

type translateRequest struct {
	Word                string   `json:"word"`
	ExistingCollections []string `json:"existing_collections,omitempty"`
	Model               string   `json:"model,omitempty"`
}

type translateResponse struct {
	Translation           string   `json:"translation"`
	Examples              []string `json:"examples"`
	Pronunciation         string   `json:"pronunciation"`
	Reason                string   `json:"reason"`
	Suggestion            string   `json:"suggestion"`
	SuggestionTranslation string   `json:"suggestion_translation"`
	MatchedCollections    []string `json:"matched_collections"`
	SuggestedCollections  []string `json:"suggested_collections"`
	Difficulty            int      `json:"difficulty"`
}

// translateRaw mirrors translateResponse but with the camelCase JSON
// keys that OpenRouter/the model actually emits.
type translateRaw struct {
	Translation           string   `json:"translation"`
	Examples              []string `json:"examples"`
	Pronunciation         string   `json:"pronunciation"`
	Reason                string   `json:"reason"`
	Suggestion            string   `json:"suggestion"`
	SuggestionTranslation string   `json:"suggestionTranslation"`
	MatchedCollections    []string `json:"matchedCollections"`
	SuggestedCollections  []string `json:"suggestedCollections"`
	Difficulty            int      `json:"difficulty"`
}

type generateCollectionRequest struct {
	CollectionName   string `json:"collection_name"`
	Description      string `json:"description,omitempty"`
	WordCount        int    `json:"word_count"`
	TargetDifficulty int    `json:"target_difficulty,omitempty"`
	Model            string `json:"model,omitempty"`
}

type generateCollectionResponse struct {
	Words []generatedWord `json:"words"`
}

type generatedWord struct {
	Word          string   `json:"word"`
	Translation   string   `json:"translation"`
	Examples      []string `json:"examples"`
	Pronunciation string   `json:"pronunciation"`
	Reason        string   `json:"reason"`
	Difficulty    int      `json:"difficulty"`
}

// generatedCollectionRaw matches what the model emits: a top-level
// object with a "words" field whose items use the same shape as
// generatedWord (camelCase → snake_case isn't needed here because the
// prompt already uses lowercase keys).
type generatedCollectionRaw struct {
	Words []generatedWord `json:"words"`
}

// ---------- Handlers ------------------------------------------------

// POST /v1/ai/translate
func (h *handlers) aiTranslate(w http.ResponseWriter, r *http.Request) {
	userID := auth.MustUserIDFromCtx(r.Context())

	if !h.aiLimiter.Allow(userID) {
		writeError(w, http.StatusTooManyRequests, "rate_limited",
			"AI request limit exceeded, try again shortly")
		return
	}

	var body translateRequest
	if !decodeJSON(w, r, &body) {
		return
	}
	if body.Word == "" {
		writeError(w, http.StatusBadRequest, "missing_fields", "word is required")
		return
	}
	model, ok := requestedModel(w, body.Model)
	if !ok {
		return
	}

	ctx, cancel := context.WithTimeout(r.Context(), 45*time.Second)
	defer cancel()

	content, err := h.ai.ChatCompletion(ctx, []ai.Message{
		{Role: "system", Content: ai.TranslateSystemPrompt},
		{Role: "user", Content: ai.TranslateUserPrompt(body.Word, body.ExistingCollections)},
	}, ai.ChatOptions{
		Model:       model,
		Temperature: 0.3,
		MaxTokens:   500,
		JSONMode:    true,
	})
	if err != nil {
		h.handleAIError(w, "ai translate", err)
		return
	}

	var raw translateRaw
	if err := json.Unmarshal([]byte(ai.StripJSONFences(content)), &raw); err != nil {
		h.logger.Error("parse ai translate json", "err", err, "content", content)
		writeError(w, http.StatusBadGateway, "ai_parse_error",
			"AI response was not valid JSON")
		return
	}

	resp := translateResponse(raw) // field names match 1:1, types are identical

	// Clamp difficulty to the documented 1–10 range. Models occasionally
	// return 0 or 11 even when the prompt forbids it.
	if resp.Difficulty < 1 {
		resp.Difficulty = 1
	}
	if resp.Difficulty > 10 {
		resp.Difficulty = 10
	}

	writeJSON(w, http.StatusOK, resp)
}

// POST /v1/ai/generate-collection
func (h *handlers) aiGenerateCollection(w http.ResponseWriter, r *http.Request) {
	userID := auth.MustUserIDFromCtx(r.Context())

	if !h.aiLimiter.Allow(userID) {
		writeError(w, http.StatusTooManyRequests, "rate_limited",
			"AI request limit exceeded, try again shortly")
		return
	}

	var body generateCollectionRequest
	if !decodeJSON(w, r, &body) {
		return
	}
	if body.CollectionName == "" || body.WordCount <= 0 {
		writeError(w, http.StatusBadRequest, "missing_fields",
			"collection_name and positive word_count are required")
		return
	}
	if body.WordCount > 100 {
		writeError(w, http.StatusBadRequest, "too_many",
			"word_count must be <= 100")
		return
	}
	targetDifficulty := body.TargetDifficulty
	if targetDifficulty < 1 || targetDifficulty > 10 {
		targetDifficulty = 5
	}
	model, ok := requestedModel(w, body.Model)
	if !ok {
		return
	}

	ctx, cancel := context.WithTimeout(r.Context(), 120*time.Second) // generation can be slow
	defer cancel()

	content, err := h.ai.ChatCompletion(ctx, []ai.Message{
		{Role: "system", Content: ai.GenerateCollectionSystemPrompt},
		{Role: "user", Content: ai.GenerateCollectionUserPrompt(body.CollectionName, body.Description, body.WordCount, targetDifficulty)},
	}, ai.ChatOptions{
		Model:       model,
		Temperature: 0.7,
		MaxTokens:   maxTokensForGeneratedWords(body.WordCount),
		JSONMode:    true,
	})
	if err != nil {
		h.handleAIError(w, "ai generate-collection", err)
		return
	}

	var raw generatedCollectionRaw
	if err := json.Unmarshal([]byte(ai.StripJSONFences(content)), &raw); err != nil {
		h.logger.Error("parse ai generate-collection json", "err", err, "content", content)
		writeError(w, http.StatusBadGateway, "ai_parse_error",
			"AI response was not valid JSON")
		return
	}

	// Clamp difficulties on every generated word.
	for i := range raw.Words {
		if raw.Words[i].Difficulty < 1 {
			raw.Words[i].Difficulty = 1
		}
		if raw.Words[i].Difficulty > 10 {
			raw.Words[i].Difficulty = 10
		}
	}

	writeJSON(w, http.StatusOK, generateCollectionResponse{Words: raw.Words})
}

func requestedModel(w http.ResponseWriter, model string) (string, bool) {
	model = ai.NormalizeModelID(model)
	if model == "" {
		return "", true
	}
	if !ai.IsAllowedModel(model) {
		writeError(w, http.StatusBadRequest, "unsupported_model", "model is not supported")
		return "", false
	}
	return model, true
}

func maxTokensForGeneratedWords(count int) int {
	maxTokens := 1500 + count*120
	if maxTokens < 4000 {
		return 4000
	}
	if maxTokens > 14000 {
		return 14000
	}
	return maxTokens
}

// handleAIError turns errors from the ai package into the right HTTP
// status + response body. Keeps per-handler error mapping consistent.
func (h *handlers) handleAIError(w http.ResponseWriter, op string, err error) {
	// Client not configured → the server doesn't have an API key.
	// 503 with a clear message so the caller knows it's a deployment
	// issue, not their fault.
	if errors.Is(err, ai.ErrNotConfigured) {
		h.logger.Warn("ai not configured", "op", op)
		writeError(w, http.StatusServiceUnavailable, "ai_not_configured",
			"OpenRouter API key not set on the server")
		return
	}

	// Upstream status error. If the upstream 4xx'd us we pass that
	// through as 502 Bad Gateway — the client can't fix it, but it's
	// useful to know OpenRouter rejected something.
	var stErr *ai.StatusError
	if errors.As(err, &stErr) {
		h.logger.Error("ai upstream error", "op", op, "upstream_status", stErr.StatusCode, "body", stErr.Body)
		writeError(w, http.StatusBadGateway, "ai_upstream_error",
			"OpenRouter returned an error")
		return
	}

	// Everything else: transport error, parse failure, timeout.
	h.logger.Error("ai internal error", "op", op, "err", err)
	writeError(w, http.StatusInternalServerError, "internal", "AI request failed")
}
