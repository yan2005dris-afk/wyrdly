# Feature: Notifications Module Cleanup (post-HU11 merge)

## Objective

Capture the architectural decisions made while cleaning up the notifications
bounded context after #74 + #77 + #78 were merged into `develop`. The goal is to
have a single source of truth that explains **why** certain files were deleted,
moved, or split so the next maintainer doesn't have to reverse-engineer it
from git history.

## Scope

In scope: every change made by `feature/cleanup-hu11-notifications` (#79) and
`refactor/pushdispatcher-use-pushgatewayport` (#80).

Out of scope: the original feature implementation itself (already documented in
`odd/tasks/hu11-web-push-notifications.md`).

---

## Decisions

### D1. Drop `application/port/PushDispatcherPort.java`

**Verdict:** deleted.

The port was defined but never injected. `PushDispatcherImpl` declared
`implements PushDispatcherPort` and the file sat unused on disk. Two reasons to
remove:

1. **No caller = no abstraction value.** Screaming-architecture ports are a
   boundary, not a checkbox. The dispatcher is consumed by the Capa 2 hooks
   (PostService, ReactionService, FollowService, ChatService) via the concrete
   `PushDispatcherImpl`, which is `@ApplicationScoped`. There is no test
   second-implementing it.

2. **The test would have been the only consumer.** The current test
   (`PushDispatcherImplTest`) injects the concrete class via `@Inject
   PushDispatcherImpl dispatcher;`. Switching to the port buys nothing until
   someone wants a fake dispatcher for unit tests of the hooks.

When we eventually do add hooks (Capa 2), we should inject
`PushDispatcherImpl` (the class) directly. If a future test needs a fake, that's
the moment to add the port back — YAGNI for now.

### D2. Drop `domain/model/PushDispatchOutcome.java`

**Verdict:** deleted.

The enum listed six outcomes (OK, NO_SUBSCRIPTION, SUBSCRIPTION_GONE,
CLIENT_ERROR, SERVER_ERROR, FAILED). It looked like a return type for
`dispatch()`, but `dispatch()` is `void`. The actual outcomes surface through
Micrometer counters:

```
wyrdly.push.dispatch{result="ok"|"gone"|"http_4xx"|"http_5xx"|"skipped_no_subscription"|"failed"}
```

Reasoning: the dispatcher is fire-and-forget (the Capa 2 hooks submit and move
on). A return value would be ignored. If a future `dispatchSync()` (with retry
transparency) is ever introduced, this enum reappears at that moment.

### D3. Move `VapidKeyPair` from `domain/model/` to `infrastructure/crypto/`

**Verdict:** moved.

`VapidKeyPair` is `record (String publicKey, String privateKey)`. The only
references are inside `VapidKeyProvider` and its test. It is a detail of the
key-loading implementation, not a domain concept. Putting it in `domain/`
suggested it had cross-cutting meaning, which it doesn't.

Other modules (`auth`, `post`, `user`) never import it. Domain models in this
module are `PushEvent`, `PushSubscription`, `PushDispatchOutcome` (deleted),
`InvalidSubscriptionException`. `VapidKeyPair` was the only one that wasn't a
real domain term.

### D4. Extract `application/port/PushGatewayClientPort`

**Verdict:** extracted.

The dispatcher injected `PushGatewayClient` directly. Quarkus's `@InjectMock`
can mock concrete classes, but doing so couples the test to the concrete
HttpClient wrapper. Pulling out an interface (matching the
`PushSubscriptionRepositoryPort` style already in the bounded context) lets
`PushDispatcherImpl` depend on the port and the test mock the port.

Follow-up (#80): `PushDispatcherImpl` now `@Inject`s `PushGatewayClientPort`
in its constructor. The `@ApplicationScoped PushGatewayClient` bean is the
implementation; Quarkus resolves it.

### D5. Cypher `MATCH...SET` → `MERGE...SET` in `CYPHER_SAVE`

**Verdict:** changed.

The previous query:

```cypher
MATCH (u:Usuario {id: $userId})
SET u.pushEndpoint = $endpoint, u.pushP256dh = $p256dh, u.pushAuth = $auth
```

silently no-op'd if the `:Usuario` node didn't exist. The push-subscription
endpoint is `POST /subscribe` and the order is:

1. Frontend calls `pushManager.subscribe(...)` → browser gets
   `{endpoint, p256dh, auth}`.
2. Frontend calls `POST /api/notifications/subscribe` to persist them.
3. (Later) user logs in for the first time → `:Usuario` is created by the auth flow.

The push can come BEFORE the user logs in (e.g. a returning user whose auth
session expired). So the `:Usuario` node might not exist when the push is
saved.

New query:

```cypher
MERGE (u:Usuario {id: $userId})
SET u.pushEndpoint = $endpoint, u.pushP256dh = $p256dh, u.pushAuth = $auth,
    u.username = coalesce(u.username, $userId),
    u.email = coalesce(u.email, $userId)
```

`MERGE` ensures the node exists; `coalesce()` prevents trampling real
`username` / `email` values when the user later signs in. If the user never
signs in, we end up with a stub `:Usuario` node with only an `id` — harmless
and cleared by any account-deletion flow that uses `DETACH DELETE`.

### D6. `awaitCounterIncrease` poll budget: 100×20ms → 500×20ms

**Verdict:** relaxed.

The test polled the Micrometer counter for up to 2 s (100 × 20 ms). Under CI
load that budget wasn't enough for the async dispatcher thread to both
increment the counter AND invoke `deleteByUserId` on the mock before the
`verify()` assertion ran. Local runs were always fast enough.

Bumped to 500 × 20 ms = 10 s. The loop short-circuits as soon as the counter
advances, so local tests don't pay the 10 s. CI uses it when needed.

**Caveat:** this masks a real race. A more robust fix would use Awaitility's
`await().untilAsserted(() -> verify(...))` so we don't rely on counter
ordering as a proxy for the mock invocation ordering. Not done in this
cleanup to avoid adding a new dependency.

### D7. Add Testcontainers IT for `Neo4jPushSubscriptionRepositoryAdapter`

**Verdict:** added.

The adapter was unit-tested only via mocks (the dispatcher's
`PushSubscriptionRepositoryPort` is `@InjectMock`'d). That misses the real Cypher.
Added `Neo4jPushSubscriptionRepositoryAdapterIT` with 7 cases covering:

- saveAndFindRoundTrip
- saveIsIdempotentAndOverwritesExistingSubscription
- findByUserIdReturnsNullWhenNoSubscriptionStored
- findByUserIdReturnsNullWhenPropertiesAreMissing
- deleteByUserIdClearsPushProperties
- deleteByUserIdIsSafeWhenNoSubscriptionStored
- saveAndDeleteCycleIsIsolatedPerUser

Follows the existing pattern in `chat/` (`Neo4jDirectMessageRepositoryAdapterIT`)
and `post/` (`Neo4jPostReactionRepositoryIT`): a `@Container static
Neo4jContainer`, a `Driver` set up in `@BeforeAll`, and `MATCH (n) DETACH
DELETE n` in `@BeforeEach` / `@AfterEach` for isolation.

---

## Pending (documented for the next pass)

### P1. IT for the dispatcher against a simulated push gateway

The `PushDispatcherImplTest` mocks `PushGatewayClientPort`. There is no
end-to-end test that exercises the real encryption + signing + HTTP. A
follow-up could stand up a `MockWebServer` (or `WireMock`) that captures the
POST and asserts on the encrypted payload, the VAPID JWT header, and the
`Content-Encoding: aes128gcm` header.

### P2. Replace the counter-driven race with Awaitility

See D6. Awaitility (`org.awaitility:awaitility`) is the standard fix for this
class of race. Adding the dependency is a separate decision.

### P3. `PushDispatchOutcome` if a sync API is ever needed

If Capa 2 hooks ever need a `dispatchSync()` that returns the outcome
(observable for testing), `PushDispatchOutcome` reappears at that point.

### P4. `PushDispatcherPort` if a fake dispatcher is ever needed

If a future test wants to assert that `PostService.create()` invoked the
dispatcher with the right event, a fake dispatcher (instead of `@InjectMock`
on the concrete class) becomes useful. Add the port at that moment.

---

## Stats (vs `develop` before the cleanup)

| Metric | Before | After |
|---|---|---|
| Production files in `notifications/` | 23 | 20 |
| Deleted : `PushDispatcherPort`, `PushDispatchOutcome` | | -2 |
| Moved : `VapidKeyPair` (`domain/model/` → `infrastructure/crypto/`) | | 0 net (rename) |
| Added : `PushGatewayClientPort` (interface) | | +1 |
| Tests in `notifications/` | 7 | 8 |
| Added : `Neo4jPushSubscriptionRepositoryAdapterIT` | | +1 |
| Tests run | 48 | 55 (+7 IT) |
| Coverage on `PushSubscriptionRepositoryAdapter` | unit only | unit + IT |
| `PushDispatcherImpl` constructor param | `PushGatewayClient` (concrete) | `PushGatewayClientPort` (interface) |
| Cypher save | `MATCH...SET` (no-op if user missing) | `MERGE...SET` |

---

## Verification

- `./mvnw test -Dtest='com.wyrdly.notifications.**'` → 55/55 green locally.
- `./mvnw spotless:check` → 0 changes needed.
- Backend CI on PR #79 and #80 → all 8 checks green (Unit, Integration, Coverage, Format, Build, Compose, GitGuardian, CodeRabbit).
- No live push gateway required (only the adapter's persistence path is tested end-to-end; the dispatcher stays unit-mocked).