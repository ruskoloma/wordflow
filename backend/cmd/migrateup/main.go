package main

import (
	"log"
	"os"
	"os/exec"
)

func main() {
	databaseURL := os.Getenv("DATABASE_URL")
	if databaseURL == "" {
		log.Fatal("DATABASE_URL is not set")
	}

	cmd := exec.Command("/migrate", "-path", "/migrations", "-database", databaseURL, "up")
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr

	if err := cmd.Run(); err != nil {
		log.Fatalf("run database migrations: %v", err)
	}
}
