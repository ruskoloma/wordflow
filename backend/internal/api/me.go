package api

import (
	"net/http"

	"github.com/rsln-ua/wordflow-backend/internal/auth"
)

// me returns the Clerk user id of the authenticated caller. Mostly a
// debugging / smoke-test route — handy for verifying that the Clerk
// middleware is correctly plumbing the sub claim through the context.
// Stays in the router after Phase 6 because it's cheap and occasionally
// useful for the Android app to prove its auth is working.
func (h *handlers) me(w http.ResponseWriter, r *http.Request) {
	userID := auth.MustUserIDFromCtx(r.Context())
	writeJSON(w, http.StatusOK, map[string]string{
		"user_id": userID,
	})
}
