// Package main is a dev-only CLI for minting short-lived Clerk session
// JWTs against a test instance. It exists so we can curl protected
// routes during development without driving the hosted sign-in UI
// every time.
//
// Flow:
//  1. Read CLERK_SECRET_KEY from env.
//  2. Either reuse an existing Clerk user id (-user flag) or create a
//     fresh test user via user.Create.
//  3. Open a session for that user via session.Create — Clerk documents
//     this endpoint as "intended only for use in testing, and is not
//     available for production instances," which is exactly our use
//     case.
//  4. Mint a session token (a signed JWT) via session.CreateToken.
//  5. Print the JWT on stdout so it can be piped into curl:
//       TOKEN=$(make devtoken)
//       curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/v1/me
//
// Not compiled into the production Docker image (the Dockerfile only
// builds ./cmd/server). Safe to leave committed.
package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"os"
	"time"

	"github.com/clerk/clerk-sdk-go/v2"
	"github.com/clerk/clerk-sdk-go/v2/session"
	"github.com/clerk/clerk-sdk-go/v2/user"
)

func main() {
	existingUserID := flag.String("user", "", "Existing Clerk user id to mint for. If empty, a new test user is created and its id is printed to stderr.")
	flag.Parse()

	secret := os.Getenv("CLERK_SECRET_KEY")
	if secret == "" {
		log.Fatal("CLERK_SECRET_KEY is required")
	}
	clerk.SetKey(secret)

	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()

	userID := *existingUserID
	if userID == "" {
		// Make the email unique-per-run so collisions never happen.
		// example.com is the reserved-for-documentation TLD that
		// passes Clerk's email validator while making it obvious in
		// the dashboard that these are throwaway test users.
		email := fmt.Sprintf("wordflow-dev+%d@example.com", time.Now().Unix())
		skipPw := true
		u, err := user.Create(ctx, &user.CreateParams{
			EmailAddresses:          &[]string{email},
			SkipPasswordRequirement: &skipPw,
		})
		if err != nil {
			log.Fatalf("user.Create: %v", err)
		}
		userID = u.ID
		fmt.Fprintf(os.Stderr, "created clerk user %s (email %s)\n", userID, email)
	}

	sess, err := session.Create(ctx, &session.CreateParams{
		UserID: userID,
	})
	if err != nil {
		log.Fatalf("session.Create (is CLERK_SECRET_KEY for a test instance?): %v", err)
	}
	fmt.Fprintf(os.Stderr, "created clerk session %s\n", sess.ID)

	token, err := session.CreateToken(ctx, &session.CreateTokenParams{
		ID: sess.ID,
	})
	if err != nil {
		log.Fatalf("session.CreateToken: %v", err)
	}

	// Only the JWT goes to stdout so callers can use `$(make devtoken)`
	// cleanly. Everything else (diagnostics) went to stderr above.
	fmt.Println(token.JWT)
}
