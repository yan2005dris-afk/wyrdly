# HU08 — Generación de Feed por Recorrido de Grafo

> Estado: Implementado
> Depende de: HU04 (follow) ✅, HU07 (create post) ✅

## Endpoint

```
GET /api/feed?page=<n>&pageSize=<n>
```

Autenticado (JWT). `page` default 1 (1-based). `pageSize` default 20, max 50.

## Criterios

Recorrido relacional en el grafo social de Neo4j:

```cypher
MATCH (me:Usuario {id: $userId})
CALL {
  WITH me
  MATCH (me)-[:SIGUE]->(author:Usuario)-[:PUBLICA]->(p:Post)
  RETURN p, author
  UNION ALL
  WITH me
  MATCH (me)-[:PUBLICA]->(p:Post)
  RETURN p, me AS author
}
WITH DISTINCT p, author, me
OPTIONAL MATCH (p)<-[r:REACCIONA]-()
OPTIONAL MATCH (p)<-[legacyLike:LIKE]-()
OPTIONAL MATCH (p)<-[legacyLove:LOVE]-()
OPTIONAL MATCH (p)<-[legacyCelebrate:CELEBRATE]-()
OPTIONAL MATCH (me)-[userR:REACCIONA]->(p)
OPTIONAL MATCH (me)-[legacyUserR:LIKE|LOVE|CELEBRATE]->(p)
WITH p.id AS id, p.content AS content, p.mediaUrl AS mediaUrl,
     p.createdAt AS createdAt, author.id AS authorId,
     author.username AS authorUsername, author.fullName AS authorFullName,
     author.avatarUrl AS authorAvatarUrl,
     count(DISTINCT CASE WHEN r.tipo = 'LIKE' THEN r END) + count(DISTINCT legacyLike) AS likeCount,
     count(DISTINCT CASE WHEN r.tipo = 'LOVE' THEN r END) + count(DISTINCT legacyLove) AS loveCount,
     count(DISTINCT CASE WHEN r.tipo = 'CELEBRATE' THEN r END) + count(DISTINCT legacyCelebrate) AS celebrateCount,
     CASE
       WHEN userR IS NOT NULL THEN userR.tipo
       WHEN legacyUserR IS NOT NULL THEN type(legacyUserR)
       ELSE null
     END AS userReactionType
ORDER BY createdAt DESC
SKIP $skip LIMIT $limit
RETURN id, content, mediaUrl, createdAt, authorId, authorUsername,
       authorFullName, authorAvatarUrl, likeCount, loveCount,
       celebrateCount, userReactionType
```

- Filtra publicaciones creadas por usuarios seguidos (`:SIGUE`) e incluye publicaciones propias.
- Queda estrictamente prohibido el volcado global no relacional (`MATCH (p:Post) RETURN p`).
- Agrega conteos de reacciones (`LIKE`, `LOVE`, `CELEBRATE`) compatibles con relaciones `[:REACCIONA {tipo}]`.
- Retorna la reacción del usuario autenticado (`userReaction`).
- Orden cronológico inverso (`createdAt DESC`).
- Paginación con `SKIP` y `LIMIT`.

## Respuesta

```json
{
  "data": [
    {
      "id": "pst_98765432",
      "content": "Publicación de ejemplo",
      "mediaUrl": "http://localhost:8080/api/media/med_123",
      "createdAt": "2026-09-24T18:50:00Z",
      "author": {
        "id": "usr_99887766",
        "username": "roberto",
        "fullName": "Roberto Martínez",
        "avatarUrl": "http://localhost:8080/api/media/med_456"
      },
      "reactionCounts": {
        "likeCount": 4,
        "loveCount": 1,
        "celebrateCount": 0,
        "LIKE": 4,
        "LOVE": 1,
        "CELEBRATE": 0
      },
      "userReaction": "LIKE"
    }
  ],
  "meta": {
    "page": 1,
    "pageSize": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  }
}
```
