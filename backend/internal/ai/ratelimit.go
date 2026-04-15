package ai

import (
	"context"
	"log/slog"
	"sync"
	"time"

	"golang.org/x/time/rate"
)

// PerUserLimiter is a token-bucket rate limiter keyed by Clerk user id.
// Each user gets their own bucket, so one heavy user can't starve
// another. Inactive users are evicted after idleAfter so the map
// doesn't grow unbounded in a long-running process.
//
// Default sizing (see NewPerUserLimiter defaults in main.go):
//   - 30 requests per minute sustained
//   - burst of 5 (let a user make 5 back-to-back calls before throttling kicks in)
//   - evict buckets for users idle for 1 hour
//
// These values are arbitrary but cautious: OpenRouter's free tier caps
// monthly spend via credits, so a runaway bug shouldn't burn through
// the budget even if it gets close to the rate limit all day.
type PerUserLimiter struct {
	mu        sync.Mutex
	limiters  map[string]*userBucket
	rate      rate.Limit
	burst     int
	idleAfter time.Duration
}

type userBucket struct {
	limiter  *rate.Limiter
	lastUsed time.Time
}

// NewPerUserLimiter creates a limiter. requestsPerMinute is the
// sustained rate; burst is how many requests can hit at once before
// throttling begins.
func NewPerUserLimiter(requestsPerMinute, burst int) *PerUserLimiter {
	return &PerUserLimiter{
		limiters:  make(map[string]*userBucket),
		rate:      rate.Limit(float64(requestsPerMinute) / 60.0),
		burst:     burst,
		idleAfter: 1 * time.Hour,
	}
}

// Allow returns true if the request for this user is within the rate
// limit, false if it should be rejected with 429. Always safe to call
// concurrently.
func (p *PerUserLimiter) Allow(userID string) bool {
	p.mu.Lock()
	defer p.mu.Unlock()

	b, ok := p.limiters[userID]
	if !ok {
		b = &userBucket{limiter: rate.NewLimiter(p.rate, p.burst)}
		p.limiters[userID] = b
	}
	b.lastUsed = time.Now()
	return b.limiter.Allow()
}

// sweep removes buckets for users idle longer than idleAfter. Not
// exported — called internally from the background goroutine started
// by Start.
func (p *PerUserLimiter) sweep() int {
	cutoff := time.Now().Add(-p.idleAfter)
	p.mu.Lock()
	defer p.mu.Unlock()
	var removed int
	for id, b := range p.limiters {
		if b.lastUsed.Before(cutoff) {
			delete(p.limiters, id)
			removed++
		}
	}
	return removed
}

// Start launches a background goroutine that sweeps idle buckets on
// the given interval. Returns when ctx is cancelled. Safe to call once
// from main with the app's root context.
func (p *PerUserLimiter) Start(ctx context.Context, interval time.Duration, logger *slog.Logger) {
	go func() {
		t := time.NewTicker(interval)
		defer t.Stop()
		for {
			select {
			case <-ctx.Done():
				return
			case <-t.C:
				if n := p.sweep(); n > 0 && logger != nil {
					logger.Debug("ai rate-limiter swept", "evicted", n)
				}
			}
		}
	}()
}
