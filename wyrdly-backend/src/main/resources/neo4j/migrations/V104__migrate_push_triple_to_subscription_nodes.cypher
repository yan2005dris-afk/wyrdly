// ==============================================================================
// V104 — Migrate single push triple on :Usuario to :SuscripcionPush nodes
// Idempotent: re-runnable without duplicating subscriptions.
// Strategy: MERGE one :SuscripcionPush per endpoint, link it from the owner,
// then clear the legacy triple. Users keep every device they had (exactly one
// row exists per user in the old model).
// ==============================================================================

MATCH (u:Usuario)
WHERE u.pushEndpoint IS NOT NULL AND u.pushEndpoint <> ''
  AND u.pushP256dh IS NOT NULL AND u.pushAuth IS NOT NULL
MERGE (s:SuscripcionPush {endpoint: u.pushEndpoint})
ON CREATE SET s.p256dh = u.pushP256dh,
              s.auth = u.pushAuth,
              s.createdAt = datetime()
MERGE (u)-[:TIENE_SUSCRIPCION]->(s)
WITH DISTINCT u
SET u.pushEndpoint = null, u.pushP256dh = null, u.pushAuth = null;
