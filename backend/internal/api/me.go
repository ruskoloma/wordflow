package api

import (
	"net/http"

	"github.com/rsln-ua/wordflow-backend/internal/auth"
)

func (h *handlers) me(w http.ResponseWriter, r *http.Request) {
	userID := auth.MustUserIDFromCtx(r.Context())
	writeJSON(w, http.StatusOK, map[string]string{
		"user_id": userID,
	})
}
