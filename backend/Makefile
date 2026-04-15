# WordFlow backend — common dev targets.
#
# Everything runs through Docker so a local Go toolchain isn't required
# (though you may want one installed for IDE/gopls support later).

.PHONY: help up up-bg down logs restart rebuild tidy sqlc build psql clean migrate-up migrate-down migrate-version

# docker-compose-internal Postgres URL. Uses the `postgres` service name
# as the hostname (compose network DNS). Override if you need to point at
# something else.
COMPOSE_DB_URL := postgres://wordflow:wordflow@postgres:5432/wordflow?sslmode=disable

help:
	@echo "WordFlow backend — dev targets"
	@echo ""
	@echo "  make up               Bring up postgres + app (foreground)"
	@echo "  make up-bg            Same, but detached"
	@echo "  make down             Stop everything"
	@echo "  make logs             Tail app logs"
	@echo "  make restart          Restart just the app service"
	@echo "  make rebuild          Force rebuild the app image"
	@echo "  make tidy             Run 'go mod tidy' in a one-shot container"
	@echo "  make sqlc             Regenerate sqlc code from queries/*.sql"
	@echo "  make build            Run 'go build ./...' in a one-shot container"
	@echo "  make psql             Open psql against the compose postgres"
	@echo "  make migrate-up       Apply all pending migrations"
	@echo "  make migrate-down     Roll back the latest migration"
	@echo "  make migrate-version  Print current migration version"
	@echo "  make clean            Stop and delete containers + volumes (NUKES DB)"

up:
	docker compose up --build

up-bg:
	docker compose up --build -d

down:
	docker compose down

logs:
	docker compose logs -f app

restart:
	docker compose restart app

rebuild:
	docker compose build --no-cache app
	docker compose up -d app

# Populate go.sum / update go.mod using a throwaway golang container.
# Mounts the current directory in so the changes show up on the host.
tidy:
	docker run --rm -v "$(CURDIR):/app" -w /app golang:1.25-alpine sh -c "go mod tidy"

# Regenerate sqlc code from queries/*.sql. Output goes to
# internal/db/sqlc/. Safe to run any time — idempotent.
sqlc:
	docker run --rm -v "$(CURDIR):/src" -w /src sqlc/sqlc:latest generate

# Compile every package in the module using a throwaway golang container.
# Catches type errors without needing go installed on the host.
build:
	docker run --rm -v "$(CURDIR):/app" -w /app golang:1.25-alpine sh -c "go build ./..."

# Mint a Clerk session JWT for curl testing. Creates a new test user
# each time, unless you pass USER_ID=user_xxx to reuse one. The JWT
# goes to stdout; diagnostics (user id, session id) go to stderr.
#
# (USER_ID is used instead of USER because shells already export USER.)
#
# Example:
#   TOKEN=$$(make devtoken 2>/dev/null)
#   curl -H "Authorization: Bearer $$TOKEN" http://localhost:8080/v1/me
#
# Only works when CLERK_SECRET_KEY points at a Clerk test instance —
# production instances refuse session.Create.
devtoken:
	@docker run --rm \
		-v "$(CURDIR):/app" -w /app \
		--env-file ../tmp/env.local \
		golang:1.25-alpine sh -c "go run ./cmd/devtoken $(if $(USER_ID),-user $(USER_ID),)"

# Psql into the compose postgres as the wordflow user.
psql:
	docker compose exec postgres psql -U wordflow -d wordflow

# Run the golang-migrate CLI baked into the app image. `docker compose run`
# automatically starts dependencies (postgres), so this works even if the
# stack is down. --entrypoint overrides the image's default ENTRYPOINT.
# --rm cleans up the one-shot container afterwards.
migrate-up:
	docker compose run --rm --entrypoint /migrate app -path /migrations -database "$(COMPOSE_DB_URL)" up

migrate-down:
	docker compose run --rm --entrypoint /migrate app -path /migrations -database "$(COMPOSE_DB_URL)" down 1

migrate-version:
	docker compose run --rm --entrypoint /migrate app -path /migrations -database "$(COMPOSE_DB_URL)" version

# NUKES DB
clean:
	docker compose down -v
