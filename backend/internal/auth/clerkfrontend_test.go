package auth

import (
	"context"
	"io"
	"net/http"
	"net/url"
	"strings"
	"testing"
)

func TestFormPostNormalizesRotatedBearerToken(t *testing.T) {
	var call int
	c := &ClerkFrontend{
		http: &http.Client{Transport: roundTripFunc(func(r *http.Request) (*http.Response, error) {
			call++
			switch call {
			case 1:
				if got, want := r.Header.Get("Authorization"), "Bearer pk_test_fake"; got != want {
					t.Fatalf("first Authorization = %q, want %q", got, want)
				}
				return clerkTestResponse("Bearer rotated"), nil
			case 2:
				if got, want := r.Header.Get("Authorization"), "Bearer rotated"; got != want {
					t.Fatalf("second Authorization = %q, want %q", got, want)
				}
				return clerkTestResponse("rotated-again"), nil
			default:
				t.Fatalf("unexpected call %d", call)
				return nil, nil
			}
		})},
		baseURL:   "https://clerk.test",
		publicKey: "pk_test_fake",
	}

	_, next, err := c.formPost(context.Background(), "/first", c.publicKey, url.Values{})
	if err != nil {
		t.Fatalf("first formPost: %v", err)
	}
	if next != "rotated" {
		t.Fatalf("next auth = %q, want rotated", next)
	}

	_, next, err = c.formPost(context.Background(), "/second", next, url.Values{})
	if err != nil {
		t.Fatalf("second formPost: %v", err)
	}
	if next != "rotated-again" {
		t.Fatalf("next auth = %q, want rotated-again", next)
	}
}

type roundTripFunc func(*http.Request) (*http.Response, error)

func (f roundTripFunc) RoundTrip(r *http.Request) (*http.Response, error) {
	return f(r)
}

func clerkTestResponse(authHeader string) *http.Response {
	return &http.Response{
		StatusCode: http.StatusOK,
		Header:     http.Header{"Authorization": []string{authHeader}},
		Body:       io.NopCloser(strings.NewReader(`{"ok":true}`)),
	}
}
