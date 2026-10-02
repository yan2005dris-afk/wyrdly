# ADR-005: Resilience Patterns for HU09 Reaction Endpoint

**Status:** Accepted
**Date:** 2026-10-01
**Deciders:** Backend team
**Related:** [plan-accion-hu09-reacciones.md](../plan/plan-accion-hu09-reacciones.md)

## Context

The HU09 reaction endpoint (`POST /api/posts/{postId}/react`) is the highest-traffic mutation in the Wyrdly social graph. It faces three concrete risks under load:

1. **Rage-click (user double/triple-clicks the heart button):** Without dedup, each click fires an HTTP request, causing 2-3 toggles back-to-back and a racey final state.
2. **Hot node lock contention:** A viral post receiving thousands of concurrent reactions causes `:Post` node exclusive-lock contention in Neo4j, blocking writes and degrading p95 latency.
3. **Viral cascade failures:** If Neo4j or Redis becomes slow/unavailable, requests pile up in the backend thread pool, exhausting connections and causing 503s.

## Decision

Implement three complementary resilience patterns in the REST layer (between JWT auth and the use case):

### 1. Idempotency Filter (`ReactionIdempotencyFilter`)
- **Mechanism:** Redis `SETEX` with 250 ms TTL on the key `react:{userId}:{postId}` storing the response body.
- **Behavior:** A second request for the same `(userId, postId)` within 250 ms replays the cached response instead of re-executing the use case.
- **Rationale:** 250 ms is the typical rage-click window. Cross-tab/cross-device dedup is also implicit because the key is per `(userId, postId)`.

### 2. Rate Limit Filter (`ReactionRateLimitFilter`)
- **Mechanism:** bucket4j with Redis backend, 30 tokens per user per minute, refill at constant 30/min.
- **Behavior:** Excess requests return `429 Too Many Requests` with `Retry-After` header.
- **Rationale:** Without this, a single user can fire unlimited requests; combined with the hot node lock risk, this protects the backend from a single bad actor. 30/min is generous enough for legitimate UX.

### 3. Circuit Breaker (`@CircuitBreaker` on `PostResource.react()`)
- **Mechanism:** SmallRye Fault Tolerance `@CircuitBreaker(requestVolumeThreshold=20, failureRatio=0.5, delay=10s)`.
- **Behavior:** If 50% of the last 20 requests fail, the circuit opens for 10 s. Subsequent calls short-circuit to a `503 SERVICE_UNAVAILABLE` response with `code: DEPENDENCY_DOWN`.
- **Rationale:** Prevents pile-up when Neo4j or Redis is unavailable. After 10 s the circuit enters half-open and probes with one request; success closes it.

### 4. Atomic Cypher toggle (§6.4 of plan)
- All reaction logic executes in **one** `executeWrite` transaction with a single round-trip Cypher.
- The Cypher detects state (`ADDED`/`REMOVED`/`UPDATED`) and applies the correct mutation in a single lock acquisition on the `:Post` node.

## Consequences

### Positive
- **Idempotent under rage-click:** Same final state regardless of click rate.
- **Bounded blast radius:** Rate limit + circuit breaker prevent cascading failures.
- **Single round-trip:** Avoids read-modify-write race conditions on `:Post`.
- **Driver-level retry:** `TransientException` retries handled by Neo4j Java Driver with backoff + jitter.

### Negative
- **Redis is a hard dependency:** Without Redis, idempotency and rate-limit fail open (or closed, depending on config). Mitigated by healthcheck in compose and the `Redis not available → fail open` fallback in `ReactionRateLimitFilter` (logs warning, allows request).
- **Idempotency cache TTL is hardcoded at 250 ms:** Configurable via env in future. Currently not externalized.
- **Circuit breaker fallback masks errors:** Frontend must distinguish 429/503/500 to give the user the right toast.

## Alternatives Considered

### A. Optimistic locking with version column
- Add a `version` property to `:Post`. On write, `MATCH (p) WHERE p.id = $id AND p.version = $v SET p.version = $v + 1`. If 0 rows updated, retry with fresh version.
- **Rejected:** Adds retry logic complexity to every write. Worse latency under contention than single-shot atomic Cypher.

### B. Client-side dedup only (AbortController)
- Frontend cancels in-flight requests for the same `postId` when a new click arrives.
- **Rejected:** Doesn't cover cross-tab, multi-device, or stale tabs. Server-side dedup is required for correctness, not just UX.

### C. `@Bulkhead` for rate limiting
- SmallRye Fault Tolerance `@Bulkhead` provides semaphore-based concurrency limiting (e.g., 30 concurrent requests total), not per-user time-window rate limiting.
- **Rejected:** Wrong semantics. `@Bulkhead` protects the backend from total concurrency, not a single user from over-requesting. We use `@Bulkhead` separately for global concurrency if needed, but rate-limit-by-user must be bucket4j.

## Validation

- Unit tests for each filter with mocked Redis (`@InjectMock RedisDataSource`).
- Integration test (Testcontainers) for 50-thread concurrent reaction on same post, asserting exactly 50 unique `[:REACCIONA]` relationships created.
- K6 load test (`loadtest/hu09-reactions.k6.js`) at 5000 VU with thresholds: p95<80ms, p99<250ms, error rate<0.05%.

## References

- [SmallRye Fault Tolerance docs](https://smallrye.io/docs/smallrye-fault-tolerance/6.9.0/)
- [bucket4j-redis](https://bucket4j.com/)
- [Neo4j Java Driver retry behavior](https://neo4j.com/docs/java-manual/current/cypher-workflow/)
- Plan §5 (Decisiones de Arquitectura) y §6 (Diseño Backend)
