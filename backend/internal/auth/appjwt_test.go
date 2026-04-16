package auth

import (
	"strings"
	"testing"
	"time"
)

func TestAppJWTMintAndVerify(t *testing.T) {
	ConfigureAppJWT("test-secret")
	defer ConfigureAppJWT("")

	token, err := MintAppToken("user_123", "person@example.com", time.Hour)
	if err != nil {
		t.Fatalf("MintAppToken: %v", err)
	}

	subject, handled, err := VerifyAppToken(token, time.Second)
	if err != nil {
		t.Fatalf("VerifyAppToken: %v", err)
	}
	if !handled {
		t.Fatal("expected token to be handled")
	}
	if subject != "user_123" {
		t.Fatalf("subject = %q, want user_123", subject)
	}
}

func TestAppJWTExpired(t *testing.T) {
	ConfigureAppJWT("test-secret")
	defer ConfigureAppJWT("")

	token, err := MintAppToken("user_123", "", -time.Hour)
	if err != nil {
		t.Fatalf("MintAppToken: %v", err)
	}

	_, handled, err := VerifyAppToken(token, time.Second)
	if !handled {
		t.Fatal("expected token to be handled")
	}
	if err == nil || !strings.Contains(err.Error(), "expired") {
		t.Fatalf("err = %v, want expired", err)
	}
}

func TestAppJWTRejectsTampering(t *testing.T) {
	ConfigureAppJWT("test-secret")
	defer ConfigureAppJWT("")

	token, err := MintAppToken("user_123", "", time.Hour)
	if err != nil {
		t.Fatalf("MintAppToken: %v", err)
	}
	token = token[:len(token)-1] + "x"

	_, handled, err := VerifyAppToken(token, time.Second)
	if !handled {
		t.Fatal("expected token to be handled")
	}
	if err == nil || !strings.Contains(err.Error(), "signature") {
		t.Fatalf("err = %v, want signature error", err)
	}
}
