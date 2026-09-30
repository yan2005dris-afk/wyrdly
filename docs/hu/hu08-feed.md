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
MATCH (me:Usuario {id: $userId})-[:SIGUE]->(author:Usuario)-[:PUBLICA]->(p:Post)
RETURN p.id AS id, p.content AS content, p.mediaUrl AS mediaUrl, p.createdAt AS createdAt,
       author.id AS authorId, author.username AS authorUsername,
       author.fullName AS authorFullName, author.avatarUrl AS authorAvatarUrl
ORDER BY p.createdAt DESC
SKIP $skip LIMIT $limit
```

- Filtra publicaciones creadas únicamente por usuarios a los que el usuario autenticado sigue (`:SIGUE`).
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
      }
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
