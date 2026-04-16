package auth

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"sync"
	"time"
)

const appJWTIssuer = "wordflow"

var (
	appJWTSecretMu sync.RWMutex
	appJWTSecret   []byte
)

// ConfigureAppJWT sets the HMAC secret used for WordFlow app tokens.
// Empty means app-token minting and verification are disabled.
func ConfigureAppJWT(secret string) {
	appJWTSecretMu.Lock()
	defer appJWTSecretMu.Unlock()
	if secret == "" {
		appJWTSecret = nil
		return
	}
	appJWTSecret = []byte(secret)
}

// AppJWTConfigured reports whether WordFlow app tokens can be verified.
func AppJWTConfigured() bool {
	appJWTSecretMu.RLock()
	defer appJWTSecretMu.RUnlock()
	return len(appJWTSecret) > 0
}

type appJWTHeader struct {
	Alg string `json:"alg"`
	Typ string `json:"typ"`
}

type appJWTClaims struct {
	Issuer    string `json:"iss"`
	Subject   string `json:"sub"`
	Email     string `json:"email,omitempty"`
	IssuedAt  int64  `json:"iat"`
	NotBefore int64  `json:"nbf"`
	ExpiresAt int64  `json:"exp"`
}

// MintAppToken creates the long-lived token Android stores after a
// successful Clerk email-code sign-in.
func MintAppToken(userID, email string, ttl time.Duration) (string, error) {
	userID = strings.TrimSpace(userID)
	if userID == "" {
		return "", errors.New("user id is required")
	}

	secret, ok := currentAppJWTSecret()
	if !ok {
		return "", errors.New("app jwt secret is not configured")
	}

	now := time.Now().UTC()
	header := appJWTHeader{Alg: "HS256", Typ: "JWT"}
	claims := appJWTClaims{
		Issuer:    appJWTIssuer,
		Subject:   userID,
		Email:     strings.TrimSpace(email),
		IssuedAt:  now.Unix(),
		NotBefore: now.Add(-30 * time.Second).Unix(),
		ExpiresAt: now.Add(ttl).Unix(),
	}

	headerPart, err := encodeJWTJSON(header)
	if err != nil {
		return "", err
	}
	claimsPart, err := encodeJWTJSON(claims)
	if err != nil {
		return "", err
	}
	unsigned := headerPart + "." + claimsPart
	return unsigned + "." + signJWT(unsigned, secret), nil
}

// VerifyAppToken verifies a WordFlow app token. handled=false means the
// token does not look like one of ours, so callers may try another verifier.
func VerifyAppToken(token string, leeway time.Duration) (subject string, handled bool, err error) {
	parts := strings.Split(token, ".")
	if len(parts) != 3 {
		return "", false, nil
	}

	var header appJWTHeader
	if err := decodeJWTJSON(parts[0], &header); err != nil {
		return "", false, nil
	}
	if header.Alg != "HS256" {
		return "", false, nil
	}

	var claims appJWTClaims
	if err := decodeJWTJSON(parts[1], &claims); err != nil {
		return "", false, nil
	}
	if claims.Issuer != appJWTIssuer {
		return "", false, nil
	}

	secret, ok := currentAppJWTSecret()
	if !ok {
		return "", true, errors.New("app jwt secret is not configured")
	}

	unsigned := parts[0] + "." + parts[1]
	want := signJWT(unsigned, secret)
	if !hmac.Equal([]byte(parts[2]), []byte(want)) {
		return "", true, errors.New("invalid app jwt signature")
	}
	if claims.Subject == "" {
		return "", true, errors.New("app jwt missing subject")
	}

	now := time.Now().UTC()
	if claims.NotBefore != 0 && now.Add(leeway).Before(time.Unix(claims.NotBefore, 0)) {
		return "", true, errors.New("app jwt not valid yet")
	}
	if claims.ExpiresAt != 0 && now.Add(-leeway).After(time.Unix(claims.ExpiresAt, 0)) {
		return "", true, errors.New("app jwt expired")
	}

	return claims.Subject, true, nil
}

func currentAppJWTSecret() ([]byte, bool) {
	appJWTSecretMu.RLock()
	defer appJWTSecretMu.RUnlock()
	if len(appJWTSecret) == 0 {
		return nil, false
	}
	secret := make([]byte, len(appJWTSecret))
	copy(secret, appJWTSecret)
	return secret, true
}

func encodeJWTJSON(v any) (string, error) {
	b, err := json.Marshal(v)
	if err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(b), nil
}

func decodeJWTJSON(part string, dst any) error {
	b, err := base64.RawURLEncoding.DecodeString(part)
	if err != nil {
		return err
	}
	if err := json.Unmarshal(b, dst); err != nil {
		return fmt.Errorf("decode jwt json: %w", err)
	}
	return nil
}

func signJWT(unsigned string, secret []byte) string {
	mac := hmac.New(sha256.New, secret)
	_, _ = mac.Write([]byte(unsigned))
	return base64.RawURLEncoding.EncodeToString(mac.Sum(nil))
}
