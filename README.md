# WordFlow

Personal English-Russian vocabulary app. Monorepo holding the
Android client and the Go backend that serves it.

## Layout

```
.
├── android/         — Kotlin + Jetpack Compose client (Room, OkHttp)
├── backend/         — Go REST backend (chi + pgx + sqlc + Clerk)
├── .github/
│   └── workflows/
│       └── release.yml   — Builds android/ APK on version tag push
├── tmp/             — (gitignored) local secrets for docker-compose
└── README.md
```

## Android

Android project lives in [`android/`](android/). Open the folder in
Android Studio or build from the command line:

```bash
cd android
./gradlew assembleDebug
```

The app talks to the backend over HTTP (default `http://10.0.2.2:8080`
for the emulator). Override via `WORDFLOW_BACKEND_URL` in
`android/local.properties` when testing from a physical device on the
LAN.

Auto-updates: the in-app updater polls
`api.github.com/repos/ruskoloma/wordflow/releases/latest` and installs
the APK attached to the newest tag. New releases are produced by the
`release.yml` workflow whenever a `v*` tag is pushed.

## Backend

Go backend lives in [`backend/`](backend/). Local dev runs through
docker-compose (Go toolchain on the host is optional):

```bash
cd backend
make up            # Postgres + app via docker-compose
make migrate-up    # Run migrations against the compose DB
make logs          # Tail app logs
make devtoken      # Mint a Clerk session JWT for curl testing
```

Secrets (Clerk keys, OpenRouter key) are read from
[`tmp/env.local`](tmp/env.local) at the monorepo root via
`env_file: ../tmp/env.local` in `backend/docker-compose.yml`. That
path is gitignored — never commit real keys.

Full backend architecture notes live in
[`backend/internal/`](backend/internal/) — each package has a
doc comment at the top of its primary file.

## Passwordless sign-in

The login flow uses Clerk's email-code strategy with no passwords.
Android posts `{email}` to `POST /v1/auth/email/start`, the Go
backend drives Clerk's Frontend API to send a 6-digit code, and
the second call `POST /v1/auth/email/verify {state_id, code}`
exchanges the code for a session JWT.

For dev, any address containing `+clerk_test@` auto-accepts the
magic code `424242` so you don't spam your real inbox.

## Deployment

Backend is designed to deploy to Railway in Docker against a
DigitalOcean-managed PostgreSQL. Not yet wired up — see the plan
notes in-session.
