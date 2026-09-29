# HU12 — Búsqueda de Usuarios por nombre / username / bio

> Issue: [#27](https://github.com/yan2005dris-afk/yaga-social/issues/27)
> Estado: spec pendiente de implementación
> Depende de: HU04 (follow) ✅ merged, HU05 (suggestions) opcional

## Endpoint

```
GET /api/users/search?q=<texto>&page=<n>&pageSize=<n>
```

Autenticado. `q` mínimo 2 chars. `page` default 0. `pageSize` default 20, max 50.

## Criterios

Match case-insensitive con `CONTAINS` sobre:

- `Usuario.username`
- `Usuario.fullName`
- `Usuario.bio`

Excluye al usuario autenticado. Ordena por `isFollowing DESC, mutualCount DESC, username ASC`.

## Respuesta

```json
{
  "data": [
    {
      "id": "usr_abc123",
      "username": "alice",
      "fullName": "Alice Chen",
      "avatarUrl": "https://cdn.yaga.social/avatars/alice.jpg",
      "bio": "Backend dev, loves Neo4j",
      "isFollowing": false,
      "mutualConnectionSnippet": "Followed by Jon and 2 others"
    }
  ],
  "meta": {
    "page": 0,
    "pageSize": 20,
    "totalResults": 42
  }
}
```

## Cypher de referencia

```cypher
MATCH (u:Usuario)
WHERE (toLower(u.username) CONTAINS toLower($q)
    OR toLower(u.fullName) CONTAINS toLower($q)
    OR toLower(u.bio)     CONTAINS toLower($q))
  AND u.id <> $userId
WITH u,
     COUNT {
       MATCH (me:Usuario {id: $userId})-[:SIGUE]->(mutual:Usuario)-[:SIGUE]->(u)
       WHERE mutual.id <> $userId AND mutual.id <> u.id
     } AS mutualCount
RETURN u.id          AS id,
       u.username    AS username,
       u.fullName    AS fullName,
       u.avatarUrl   AS avatarUrl,
       u.bio         AS bio,
       EXISTS {
         MATCH (me:Usuario {id: $userId})-[:SIGUE]->(u)
       } AS isFollowing,
       mutualCount
ORDER BY isFollowing DESC, mutualCount DESC, u.username ASC
SKIP $skip
LIMIT $limit
```

## Checklist de implementación

- [ ] `SearchUsersUseCase` (input port)
- [ ] `UserSearchService`
- [ ] `findByText` + `countByText` en `UserRepository` (o `UserSearchRepository` aparte)
- [ ] `Neo4jUserSearchRepositoryAdapter`
- [ ] `UserResource.searchUsers(q, page, pageSize)` con `@Authenticated` + `@Valid`
- [ ] DTOs `UserSearchResultDto`, `UserSearchResponseDto`
- [ ] ExceptionMapper siguiendo `@ServerExceptionMapper` + Map.of(...)
- [ ] Tests unit del service
- [ ] Tests integración Testcontainers (username exacto, fullName parcial, bio substring, excluye auth, mutual count, isFollowing, paginación)
- [ ] Tests REST (200, 200 vacío, 400 q corto, 400 pageSize>50, 401 sin JWT)
- [ ] V003 migration: índice full-text sobre `Usuario.username`, `fullName`, `bio`
- [ ] Documentar endpoint en README / OpenAPI
