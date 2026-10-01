// ==============================================================================
// V001__create_constraints_and_indexes.cypher
// Migración Inicial: Restricciones de Unicidad e Índices para Wyrdly
// ==============================================================================

// 1. Restricciones de Unicidad para Nodos :Usuario
CREATE CONSTRAINT user_id_unique IF NOT EXISTS
FOR (u:Usuario) REQUIRE u.id IS UNIQUE;

CREATE CONSTRAINT user_username_unique IF NOT EXISTS
FOR (u:Usuario) REQUIRE u.username IS UNIQUE;

CREATE CONSTRAINT user_email_unique IF NOT EXISTS
FOR (u:Usuario) REQUIRE u.email IS UNIQUE;

// 2. Restricciones de Unicidad para Nodos :Post
CREATE CONSTRAINT post_id_unique IF NOT EXISTS
FOR (p:Post) REQUIRE p.id IS UNIQUE;

// 3. Restricciones de Unicidad para Nodos :Mensaje
CREATE CONSTRAINT message_id_unique IF NOT EXISTS
FOR (m:Mensaje) REQUIRE m.id IS UNIQUE;

// 4. Índices de Búsqueda y Recorrido
CREATE INDEX user_username_index IF NOT EXISTS
FOR (u:Usuario) ON (u.username);

CREATE INDEX post_created_at_index IF NOT EXISTS
FOR (p:Post) ON (p.createdAt);

CREATE INDEX message_created_at_index IF NOT EXISTS
FOR (m:Mensaje) ON (m.createdAt);
