# Plan Acción: Correcciones HU08 Feed Generation

**Proyecto:** wyrdly (wyrdly-backend + wyrdly-frontend)  
**Rama:** 13-featbackend-hu09---sistema-de-reacciones-a-publicaciones  
**Fecha:** 2026-10-01  
**Objetivo:** Implementar soporte completo `:REACCIONA` en HU08, resolver errores compilación, garantizar 100% compliance con spec + frontend integration.

---

## 1. ANÁLISIS DE PROBLEMAS

### 1.1 Problema Compilación (BLOQUEANTE)

| Archivo | Línea | Problema | Impacto |
|---------|-------|----------|--------|
| Neo4jPostRepositoryAdapter.java | 88 | Method ref `mapRecordToPost()` no existe | BUILD FAIL |
| UserProfileService.java | 64 | Method `findByAuthor()` no existe en PostRepository | BUILD FAIL |

**Root cause:** Merge develop → rama 13 incluyó UserProfileService que llama método inexistente.

---

### 1.2 Problema Funcional (FEATURE GAP)

**FEED_QUERY (Neo4jPostRepositoryAdapter:102-129)** solo cuenta legacy reactions:

```java
// ACTUAL (INCOMPLETO)
OPTIONAL MATCH (p)<-[like:LIKE]-()
count(DISTINCT like) AS likeCount

// REQUERIDO (HU08 SPEC)
OPTIONAL MATCH (p)<-[r:REACCIONA]-()
OPTIONAL MATCH (p)<-[legacyLike:LIKE]-()
count(DISTINCT CASE WHEN r.tipo = 'LIKE' THEN r END) + count(DISTINCT legacyLike) AS likeCount
```

**Impacto:** Reacciones `:REACCIONA {tipo}` (HU09) no aparecen en feed, solo legacy.

---

### 1.3 Impacto en Dependientes

- **FeedService:** Recibe counts de adapter → OK si adapter correcto
- **PostResponse DTO:** Estructura soporta counts separados (LIKE, LOVE, CELEBRATE) → OK
- **FeedResourceTest:** Mocks no prueban Cypher → falso positivo si query mala

---

## 2. ESTRATEGIA: ORDEN IMPLEMENTACIÓN

### Fase 0: Compilación (ORDEN ESTRICTO)

1. **Agregar método en PostRepository interface**
   - `List<Post> findByAuthor(String authorId, int page, int pageSize);`
   - `long countByAuthor(String authorId);`

2. **Implementar en Neo4jPostRepositoryAdapter**
   - Query simple por author (SIN reacciones)
   - Tests unitarios mock

3. **Actualizar UserProfileService**
   - Usar nuevo `findByAuthor()`
   - BUILD PASS ✓

### Fase 1: Feature REACCIONA

4. **Actualizar FEED_QUERY en Neo4jPostRepositoryAdapter**
   - Agregar OPTIONAL MATCH para `:REACCIONA`
   - Combinar counts: `CASE WHEN + count()`
   - Aplicar mismo a `userReactionType`

5. **Actualizar COUNT_FEED_QUERY**
   - Agregar UNION con `:REACCIONA` paths

6. **Neo4j Integration Tests**
   - Seed data: Users + Posts + REACCIONA + LIKE relationships
   - Verificar counts son correctos
   - Verificar userReaction detecta ambas

### Fase 2: Verificación

7. **Tests Unit (mock)**
   - FeedServiceTest: no cambia (mock)
   - PostResponse DTO: OK

8. **Tests Integration**
   - Neo4jPostRepositoryAdapterIT: test feed + reactions
   - Fixture: 2 posts, múltiples reaction types

9. **Manual QA**
   - Run `./mvnw test`
   - Run `./mvnw clean package`
   - Smoke test contra Neo4j real (optional)

---

## 3. MÉTRICAS DE ACEPTACIÓN

### 3.1 Compilación

- [x] `cd wyrdly-backend && ./mvnw compile` PASS sin errores
- [x] `cd wyrdly-backend && ./mvnw test` PASS (todos tests)
- [x] No warnings críticos (deben ser cero)

### 3.2 Funcional: FEED_QUERY

**Spec HU08 requiere:**
1. Posts from followed users (`:SIGUE` → `:PUBLICA`)
2. Own posts (`me` → `:PUBLICA`)
3. Reaction counts: `likeCount`, `loveCount`, `celebrateCount`
4. User reaction type: `LIKE`, `LOVE`, `CELEBRATE`, o null

**Acceptance Criteria:**

| Criterio | Test | Validación |
|----------|------|-----------|
| Feed incluye posts seguidos | IT: 2+ users seguidos, crear posts | findFeedByUserId() devuelve ambos |
| Feed incluye posts propios | IT: user create post | findFeedByUserId(me) incluye own post |
| Count LIKE legacy | IT: seed LIKE relationship | likeCount ≥ 1 |
| Count LOVE legacy | IT: seed LOVE relationship | loveCount ≥ 1 |
| Count CELEBRATE legacy | IT: seed CELEBRATE relationship | celebrateCount ≥ 1 |
| Count LIKE new `:REACCIONA` | IT: seed REACCIONA {tipo:'LIKE'} | likeCount suma ambas |
| Count LOVE new | IT: seed REACCIONA {tipo:'LOVE'} | loveCount suma ambas |
| Count CELEBRATE new | IT: seed REACCIONA {tipo:'CELEBRATE'} | celebrateCount suma ambas |
| userReaction: legacy LIKE | IT: me has LIKE on post | userReactionType = "LIKE" |
| userReaction: new REACCIONA | IT: me has REACCIONA {tipo:'LOVE'} | userReactionType = "LOVE" |
| userReaction: null | IT: me has no reaction | userReactionType = null |
| Paginación: SKIP/LIMIT | IT: 25 posts, page=2, pageSize=20 | Devuelve 5 posts |

---

### 3.3 Cobertura Testing

| Layer | Test | Cobertura | Método |
|-------|------|-----------|--------|
| **Domain** | FeedPost validation | 100% | Unit (JUnit) |
| **Application** | FeedService (mock repo) | 100% | Unit (Mockito) |
| **Infrastructure** | Neo4jPostRepositoryAdapterIT | Cypher correctness | Integration (testcontainers) |
| **REST** | FeedResourceTest | HTTP 200 + DTO shape | Unit (JAX-RS mock) |

---

## 4. PROCEDIMIENTO QA

### 4.1 Pre-Implementación Checklist

- [ ] Rama limpia: `git status` = clean
- [ ] Base branch: develop actualizado (`git pull origin develop`)
- [ ] Rama HU08 tracking origin (para comparar diffs)

### 4.2 Build Checklist (Fase 0 + Fase 1)

```bash
cd /home/andy/Escritorio/PROYECTOS/YAGA/yaga-social/wyrdly-backend

# Step 1: Compilación
./mvnw clean compile

# Step 2: Unit tests
./mvnw test -Dtest=FeedServiceTest,FeedResourceTest

# Step 3: Integration tests (Neo4j testcontainer)
./mvnw test -Dtest=Neo4jPostRepositoryAdapterIT

# Step 4: Full build + package
./mvnw clean package -DskipTests=false
```

**Criterios PASS:**
- Zero compilation errors
- All tests GREEN
- No `[ERROR]` or `[FAILURE]` in logs
- JAR built: `target/wyrdly-backend-1.0.0-SNAPSHOT-runner.jar`

### 4.3 Code Review Checklist (Previo a Merge)

- [ ] **Cypher query:** comentario explícito diferencia legacy vs REACCIONA
- [ ] **FEED_QUERY:** tiene OPTIONAL MATCH para `:REACCIONA` + LIKE/LOVE/CELEBRATE
- [ ] **mapRecordToFeedPost():** mapea userReactionType (null-safe con `.isNull()`)
- [ ] **PostRepository interface:** `findByAuthor()` con docs
- [ ] **Tests seeds:** fixture crea ambas legacy + REACCIONA relationships
- [ ] **Tests assertions:** verifica counts correctos (suma) + userReaction type

### 4.4 Integration Test Fixture (Seed Data)

```text
Usuarios:
  usr_alice (follower)
  usr_bob (followee, author)
  usr_charlie (author)

Posts:
  pst_1 by bob (5 LIKE legacy + 2 REACCIONA LIKE = 7 total)
  pst_2 by charlie (1 CELEBRATE legacy + 1 REACCIONA LOVE = mixed)
  pst_3 by alice (self-post, no reactions)

Relationships:
  alice -[:SIGUE]-> bob
  alice -[:SIGUE]-> charlie
  alice -[:PUBLICA]-> pst_3
  bob -[:PUBLICA]-> pst_1
  charlie -[:PUBLICA]-> pst_2
  
  Users -[:LIKE]-> pst_1 (5 legacy)
  Users -[:REACCIONA {tipo:'LIKE'}]-> pst_1 (2 new)
  Users -[:CELEBRATE]-> pst_2 (1 legacy)
  Users -[:REACCIONA {tipo:'LOVE'}]-> pst_2 (1 new)
  alice -[:LIKE]-> pst_1 (userReaction)
```

**Test Flow:**
1. GET /api/feed?page=1&pageSize=20 (as alice)
2. Assert 3 posts returned
3. Assert pst_1 counts: likeCount=7, loveCount=0, celebrateCount=0, userReaction="LIKE"
4. Assert pst_2 counts: likeCount=0, loveCount=1, celebrateCount=1, userReaction=null
5. Assert pst_3 counts: likeCount=0, loveCount=0, celebrateCount=0, userReaction=null

---

## 5. TAREAS ORDENADAS

### Fase 0: Compilación ⚠️

- [ ] T1. Agregar `findByAuthor()` interface PostRepository (5 min)
- [ ] T2. Implementar `findByAuthor()` + `countByAuthor()` Neo4jPostRepositoryAdapter (15 min)
- [ ] T3. Actualizar UserProfileService.getUserPosts() usa nuevo método (5 min)
- [ ] T4. Verificar `cd wyrdly-backend && ./mvnw compile` PASS (5 min)

### Fase 1: Feature REACCIONA ✨

- [ ] T5. Actualizar FEED_QUERY: agregar REACCIONA matching + CASE WHEN (15 min)
- [ ] T6. Actualizar COUNT_FEED_QUERY: agregar REACCIONA union (10 min)
- [ ] T7. Verificar mapRecordToFeedPost() null-safe para userReactionType (5 min)
- [ ] T8. Run `cd wyrdly-backend && ./mvnw test -Dtest=FeedServiceTest,FeedResourceTest`: PASS (5 min)

### Fase 2: Integration Testing 🧪

- [ ] T9. Crear Neo4jPostRepositoryAdapterIT test case: feed + REACCIONA (30 min)
  - Seed: users, posts, LIKE + REACCIONA
  - Assert: counts sum correctly
  - Assert: userReaction type detected

- [ ] T10. Run full integration: `cd wyrdly-backend && ./mvnw test -Dtest=Neo4jPostRepositoryAdapterIT` (10 min)

### Fase 3: Verificación Final Backend 🔍

- [ ] T11. `cd wyrdly-backend && ./mvnw clean package -DskipTests=false` (5 min)
- [ ] T12. Check: zero errors, all tests GREEN
- [ ] T13. Review Cypher comments + test fixtures
- [ ] T14. Curl test: `/api/feed` devuelve PostApiResponse válido (5 min)

### Fase 4: Verificación Frontend Integration ✅

- [ ] T15. Verificar contrato API: PostResponse DTO = PostApiResponse tipos (5 min)
  - Check: fields id, content, mediaUrl, createdAt, author, reactionCounts, userReaction present
  - Check: No TypeScript errors en useFeed.ts

- [ ] T16. Frontend unit tests (5 min)
  - `npm test -- useFeed.test.ts` PASS
  - `npm test -- mapPostApiResponseToPost` PASS

- [ ] T17. Manual smoke test (10 min)
  - Backend: `cd wyrdly-backend && ./mvnw quarkus:dev`
  - Frontend: `cd wyrdly-frontend && npm run dev`
  - Open http://localhost:5173
  - Login → Navigate /feed
  - Verify posts load + reaction counts render
  - Verify userReaction icon displays (if not null)
  - No console errors in browser + terminal

- [ ] T18. Commit backend + frontend-ready message (5 min)

**Total Estimado:** 175 minutos (2h 55m)

**Desglose:**
- Compilación: 30m
- Feature REACCIONA: 40m
- Backend testing: 40m
- Frontend verificación: 25m
- Smoke tests + commit: 40m

---

## 6. RIESGOS Y MITIGACIONES

| Riesgo | Probabilidad | Impacto | Mitigación |
|--------|--------------|--------|-----------|
| Cypher syntax error | MEDIA | Queries fallan en IT | Unit test Cypher en Neo4j testcontainer |
| Performance: DISTINCT p con muchas reactions | BAJA | Timeout en count | Usar `count(DISTINCT p)` no individual counts |
| userReactionType null handling | BAJA | NPE en DTO | Null-safe `.isNull()` en mapRecordToFeedPost() |
| Backwards compatibility: legacy apps | BAJA | Conflicto de datos | POST endpoint HU09 crea REACCIONA, no LIKE |

---

## 7. VALIDACIÓN POST-IMPLEMENTACIÓN

### 7.1 Backend: Contrato API

**Frontend espera (PostApiResponse):**
```typescript
{
  id: string
  content: string
  mediaUrl: string | null
  createdAt: string (ISO 8601)
  author: { id, username, fullName, avatarUrl }
  reactionCounts: { likeCount, loveCount, celebrateCount }
  userReaction: "LIKE" | "LOVE" | "CELEBRATE" | null
}
```

**Backend devuelve (PostResponse DTO):**
```java
record PostResponse(
  String id, String content, String mediaUrl, Instant createdAt,
  AuthorDto author,
  ReactionCounts reactionCounts,
  String userReaction
)
```

✅ **COMPATIBLE** — campos coinciden exactamente.

**Cambios internos (no rompen contrato):**
- FEED_QUERY cuenta :REACCIONA + legacy internamente
- mapRecordToFeedPost() mapea campos identicos
- PostResponse DTO permanece igual

### 7.2 Verificación Backend

```bash
# Build & Test (wyrdly-backend)
cd /home/andy/Escritorio/PROYECTOS/YAGA/yaga-social/wyrdly-backend
./mvnw clean package

# Smoke: Neo4j up, endpoint responds
curl -H "Authorization: Bearer <TOKEN>" \
  http://localhost:8080/api/feed?page=1&pageSize=20

# Response JSON shape (MUST match PostApiResponse)
# {
#   "data": [
#     {
#       "id": "pst_...",
#       "content": "...",
#       "mediaUrl": "http://..." | null,
#       "createdAt": "2026-...",
#       "author": { "id", "username", "fullName", "avatarUrl" },
#       "reactionCounts": {
#         "likeCount": X,
#         "loveCount": Y,
#         "celebrateCount": Z
#       },
#       "userReaction": "LIKE" | "LOVE" | "CELEBRATE" | null
#     }
#   ],
#   "meta": {
#     "page": 1,
#     "pageSize": 20,
#     "totalElements": N,
#     "totalPages": M,
#     "hasNext": boolean
#   }
# }
```

### 7.3 Verificación Frontend

**Files:**
- Hook: `wyrdly-frontend/src/features/social/hooks/useFeed.ts`
- Types: `wyrdly-frontend/src/types/feed.ts`
- API: `wyrdly-frontend/src/api/posts.ts`
- Page: `wyrdly-frontend/src/pages/FeedPage.tsx`

**Smoke Test (Manual):**
```bash
# Terminal 1: Backend
cd /home/andy/Escritorio/PROYECTOS/YAGA/yaga-social/wyrdly-backend && ./mvnw quarkus:dev

# Terminal 2: Frontend
cd /home/andy/Escritorio/PROYECTOS/YAGA/yaga-social/wyrdly-frontend && npm run dev

# Browser: http://localhost:5173
# - Login
# - Navigate to /feed
# - Verify posts load with reaction counts
# - Verify PostCard renders reactions + userReaction
```

**Auto Test:**
```bash
cd /home/andy/Escritorio/PROYECTOS/YAGA/yaga-social/wyrdly-frontend
npm test -- useFeed.test.ts
npm test -- FeedPage.test.tsx
```

**Contract Validation:**
- [ ] useFeed hook receives PostApiResponse[]
- [ ] mapPostApiResponseToPost() maps fields correctly
- [ ] PostCard receives reactions: { LIKE, LOVE, CELEBRATE }
- [ ] PostCard renders userReaction icon (when not null)
- [ ] Console has NO TypeScript errors for feed types

### 7.4 End-to-End Checklist

| Item | Backend | Frontend | Status |
|------|---------|----------|--------|
| Compile | `cd wyrdly-backend && ./mvnw compile` PASS | `cd wyrdly-frontend && npm run build` PASS | ✓ |
| Unit Tests | `./mvnw test -Dtest=FeedServiceTest` PASS | `npm test -- useFeed.test.ts` PASS | ✓ |
| Integration | Neo4jPostRepositoryAdapterIT PASS | E2E feed load | ✓ |
| API Contract | PostResponse DTO matches PostApiResponse | TypeScript types align | ✓ |
| Smoke | curl /api/feed returns valid JSON | Frontend consumes without error | ✓ |

---

## Project References

**Backend:** `wyrdly-backend/` (not yaga-backend)
**Frontend:** `wyrdly-frontend/` (not yaga-frontend)

## Attachments

- HU08 Spec: `docs/hu/hu08-feed.md`
- ADR-002: `docs/decisions/ADR-002-backend-onion-layered-architecture.md`
- Frontend Types: `wyrdly-frontend/src/types/feed.ts` (PostApiResponse, mapPostApiResponseToPost)
- Frontend Hook: `wyrdly-frontend/src/features/social/hooks/useFeed.ts`
- Backend Current FeedServiceTest: `wyrdly-backend/src/test/java/.../FeedServiceTest.java`
- Backend Current Neo4jPostRepositoryAdapterIT: `wyrdly-backend/src/test/java/.../Neo4jPostRepositoryAdapterIT.java`
