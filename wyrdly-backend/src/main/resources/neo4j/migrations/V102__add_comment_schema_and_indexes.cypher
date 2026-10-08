// ==============================================================================
// V006__add_comment_schema_and_indexes.cypher
// Constraint + index for the (:Comentario) node introduced by HU10 (post comments).
// ==============================================================================

// 1. Constraint de unicidad por id (generado como "cmt_" + 12 hex)
CREATE CONSTRAINT comment_id_unique IF NOT EXISTS
FOR (c:Comentario) REQUIRE c.id IS UNIQUE;

// 2. Índice compuesto (postId, createdAt) para el feed ordenado de comentarios
CREATE INDEX comment_post_created_at_index IF NOT EXISTS
FOR (c:Comentario) ON (c.postId, c.createdAt);