// ==============================================================================
// V004__normalize_and_deduplicate_follows.cypher
// Normaliza y deduplica las relaciones [:SIGUE]
// ==============================================================================
// Reemplaza la V004 original ("add follow indexes"), que nunca llegó a aplicarse:
// su constraint sobre (u1.id, u2.id) de una relación no es Cypher válido en
// Neo4j 5 y, al correr la migración en una sola transacción, también se descartó
// su índice. La unicidad del follow la garantiza el MERGE del adapter.
// Solo modifica datos (sin cambios de esquema) y es idempotente.

// 1. La app escribía `fecha` y el seed `createdAt`: unificar en `createdAt`,
//    la convención del resto de entidades.
MATCH ()-[r:SIGUE]->()
WHERE r.fecha IS NOT NULL
SET r.createdAt = coalesce(r.createdAt, r.fecha)
REMOVE r.fecha;

// 2. El MERGE anterior incluía `fecha: datetime()` en el patrón y creaba una
//    relación nueva en cada follow repetido: conservar la más antigua por par.
MATCH (a:Usuario)-[r:SIGUE]->(b:Usuario)
WITH a, b, r
ORDER BY r.createdAt
WITH a, b, collect(r) AS rels
WHERE size(rels) > 1
FOREACH (dup IN rels[1..] | DELETE dup);
