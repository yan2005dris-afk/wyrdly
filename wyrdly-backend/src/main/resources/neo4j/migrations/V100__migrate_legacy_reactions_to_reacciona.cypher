// ==============================================================================
// V100 — Migrate legacy reactions [:LIKE|:LOVE|:CELEBRATE] to [:REACCIONA {tipo}]
// Idempotent: re-runnable without duplicating relationships.
// Strategy: MERGE ensures one REACCIONA per (user, post) pair. If user already
// has REACCIONA from a prior run, MERGE keeps existing. Then we DELETE legacy.
// ==============================================================================

MATCH (u:Usuario)-[legacy:LIKE|LOVE|CELEBRATE]->(p:Post)
WITH u, legacy, p,
     type(legacy) AS tipo,
     coalesce(legacy.createdAt, datetime()) AS createdAt
MERGE (u)-[nr:REACCIONA]->(p)
ON CREATE SET nr.tipo = tipo,
              nr.createdAt = createdAt,
              nr.updatedAt = datetime()
ON MATCH SET nr.updatedAt = datetime()
WITH DISTINCT legacy
DELETE legacy;
