# syntax=docker/dockerfile:1.7

# ---- Build stage ---------------------------------------------------------
# Pinned Go 1.22 Alpine for the build. Alpine keeps the build image small
# and has a functional toolchain for CGO-disabled builds (which is what we
# want for a pure-Go static binary).
FROM golang:1.25-alpine AS build
WORKDIR /src

# Dependencies first for better layer caching. If go.mod/go.sum don't change,
# Docker reuses the cached `go mod download` layer on every rebuild.
COPY go.mod go.sum ./
RUN go mod download

# Now bring in the source and build. CGO_ENABLED=0 produces a fully static
# binary so we can ship it in a distroless image without libc.
# -ldflags="-s -w" strips debug info, ~30% smaller binary.
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o /out/server ./cmd/server

# ---- Migrate tool stage --------------------------------------------------
# Bake the golang-migrate CLI into a separate build stage and copy the
# static binary into the runtime image. This lets us run migrations from
# the same image (Railway's release command, local `make migrate-up`, etc.)
# without pulling another image at deploy time. Pinned version for
# reproducible builds.
FROM golang:1.25-alpine AS migrate-build
RUN apk add --no-cache git \
 && go install -tags 'postgres' github.com/golang-migrate/migrate/v4/cmd/migrate@v4.19.0

# ---- Runtime stage -------------------------------------------------------
# distroless/static has no shell, no package manager, no anything — just
# enough libraries to run a static Go binary as a non-root user.
# Smallest possible attack surface for a stateless HTTP server.
FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build         /out/server      /server
COPY --from=migrate-build /go/bin/migrate  /migrate
COPY --from=build         /src/migrations  /migrations
EXPOSE 8080
USER nonroot:nonroot
ENTRYPOINT ["/server"]
