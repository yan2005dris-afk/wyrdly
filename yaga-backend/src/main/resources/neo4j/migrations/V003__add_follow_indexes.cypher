// ==============================================================================
// V003__add_follow_indexes.cypher
// Índices de Performance para Relaciones Follow
// ==============================================================================

// 1. Índice en la relación SIGUE para búsquedas de usuarios que siguen a un usuario
CREATE INDEX follow_relationship_index
  IF NOT EXISTS
  FOR ()-[r:SIGUE]-() ON (r.fecha);

// 2. Constraint de unicidad para evitar duplicados (Usuario A no puede seguir a B dos veces)
CREATE CONSTRAINT follow_unique IF NOT EXISTS
FOR (u1:Usuario)-[r:SIGUE]->(u2:Usuario)
REQUIRE (u1.id, u2.id) IS UNIQUE;
