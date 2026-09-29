// ==============================================================================
// V003__add_user_search_index.cypher
// Índice full-text para búsqueda de usuarios por username / fullName / bio
// ==============================================================================
//
// Habilita búsquedas performantes para el endpoint GET /api/users/search
// (HU12, issue #27). El índice cubre los tres campos sobre los que se hace
// match con CONTAINS, case-insensitive.

CREATE FULLTEXT INDEX user_search_index IF NOT EXISTS
FOR (u:Usuario) ON EACH [u.username, u.fullName, u.bio];