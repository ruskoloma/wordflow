package api

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestDecodeJSONRejectsTrailingValue(t *testing.T) {
	req := httptest.NewRequest(http.MethodPost, "/", strings.NewReader(`{"name":"one"} {"name":"two"}`))
	rec := httptest.NewRecorder()

	var body struct {
		Name string `json:"name"`
	}
	if decodeJSON(rec, req, &body) {
		t.Fatal("decodeJSON accepted multiple top-level JSON values")
	}
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusBadRequest)
	}
}

func TestDecodeJSONAllowsTrailingWhitespace(t *testing.T) {
	req := httptest.NewRequest(http.MethodPost, "/", strings.NewReader("{\"name\":\"one\"}\n\t "))
	rec := httptest.NewRecorder()

	var body struct {
		Name string `json:"name"`
	}
	if !decodeJSON(rec, req, &body) {
		t.Fatalf("decodeJSON rejected valid body: %s", rec.Body.String())
	}
	if body.Name != "one" {
		t.Fatalf("name = %q, want one", body.Name)
	}
}
