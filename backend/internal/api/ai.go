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

type generatedCollectionRaw struct {
	Words []generatedWord `json:"words"`
}

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
	body.Word = trimRequiredString(body.Word)
	body.ExistingCollections = trimStringSlice(body.ExistingCollections)
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

	resp := translateResponse(raw)
	resp.Difficulty = clampDifficulty(resp.Difficulty)

	writeJSON(w, http.StatusOK, resp)
}

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
	body.CollectionName = trimRequiredString(body.CollectionName)
	body.Description = trimRequiredString(body.Description)
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

	ctx, cancel := context.WithTimeout(r.Context(), 120*time.Second)
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

	for i := range raw.Words {
		raw.Words[i].Difficulty = clampDifficulty(raw.Words[i].Difficulty)
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

func (h *handlers) handleAIError(w http.ResponseWriter, op string, err error) {
	if errors.Is(err, ai.ErrNotConfigured) {
		h.logger.Warn("ai not configured", "op", op)
		writeError(w, http.StatusServiceUnavailable, "ai_not_configured",
			"OpenRouter API key not set on the server")
		return
	}

	var stErr *ai.StatusError
	if errors.As(err, &stErr) {
		h.logger.Error("ai upstream error", "op", op, "upstream_status", stErr.StatusCode, "body", stErr.Body)
		writeError(w, http.StatusBadGateway, "ai_upstream_error",
			"OpenRouter returned an error")
		return
	}

	h.logger.Error("ai internal error", "op", op, "err", err)
	writeError(w, http.StatusInternalServerError, "internal", "AI request failed")
}
