# Feature: Feed Cursor Pagination con Infinite Scroll

## Objetivo
Implementar paginación basada en cursor para el feed (`GET /api/feed`) tanto en el backend (Quarkus + Neo4j) como en el frontend (React + Vitest), eliminando los problemas de inconsistencia y duplicados de posts durante el scroll infinito y garantizando acceso $O(1)$ sobre el índice temporal en Neo4j.

## Problema
Actualmente `GET /api/feed` usa paginación clásica por offset (`page`, `pageSize`) con `SKIP $skip LIMIT $limit`.
1. Si un usuario navega el feed y se crean publicaciones nuevas en tiempo real, la paginación por offset desplaza los elementos, haciendo que el usuario reciba posts duplicados al pedir la siguiente página.
2. `SKIP` en Neo4j escanea y descarta nodos anteriores en $O(N \cdot \text{skip})$, degradando la latencia en lecturas profundas.

## Por qué
El feed es una línea temporal continua ordenada por `createdAt DESC`. La paginación por cursor temporal anclado en `p.createdAt` es la arquitectura óptima: elimina duplicados, provee rendimiento estable $O(1)$ gracias al índice `post_created_at_index` y permite una UX de scroll infinito fluida.

## Alcance Autorizado
- **Backend:**
  - Actualizar `FeedResponseDto` y su `PaginationMeta` con `nextCursor` y `hasMore`.
  - Agregar soporte de cursor opaco (Base64 codificando el timestamp `createdAt`) y parámetro `limit` en `FeedResource`, `GetFeedUseCase`, `FeedService`, `PostRepository` y `Neo4jPostRepositoryAdapter`.
  - Mantener compatibilidad retroactiva para llamadas con `page` y `pageSize`.
  - Tests unitarios y de integración con Testcontainers Neo4j.
- **Frontend:**
  - Actualizar `FeedMeta` y `postsApi.getFeed` con soporte de `cursor` y `limit`.
  - Actualizar el hook `useFeed` para exponer `loadMore()` acumulativo sin duplicados, `hasMore` y `nextCursor`.
  - Tests unitarios en Vitest.

## Restricciones
- Conventional Commits sin "Co-Authored-By" ni atribución a IA.
- Cursors opacos (Base64) en la API pública para no exponer detalles internos.
- 100% de tests pasando en backend (`mvn test`) y frontend (`pnpm test`).

## Tareas

- [x] **TASK-01**: Backend DTOs & UseCase - Extender `FeedResponseDto` con `nextCursor` y `hasMore`, actualizar firma de `GetFeedUseCase`. (Commit: `3c41925`)
- [x] **TASK-02**: Backend Repository & Adapter - Implementar consulta por cursor temporal `findFeedByUserId(String userId, String cursor, int limit)` en `Neo4jPostRepositoryAdapter`. (Commit: `3c41925`)
- [x] **TASK-03**: Backend Service & Resource - Adaptar `FeedService` y `FeedResource` para procesar/codificar cursores opacos Base64 y exponer `limit` / `cursor`. (Commit: `3c41925`)
- [x] **TASK-04**: Backend Tests - Pruebas unitarias en `FeedServiceTest`, `FeedResourceTest` e integración en `Neo4jPostRepositoryAdapterIT`. (Commit: `3c41925`)
- [x] **TASK-05**: Frontend API & Hook - Actualizar tipos en `feed.ts`, `posts.ts` y soporte de `loadMore()` en `useFeed.ts`. (Commit: `55a3b19`)
- [x] **TASK-06**: Frontend Tests - Pruebas unitarias de `useFeed.test.ts` verificando acumulación y paginación con cursor. (Commit: `55a3b19`)

## Evidencia de Verificación
- **Backend**:
  - `mvn test`: 122/122 tests pasando en verde.
  - `Neo4jPostRepositoryAdapterIT`: 16/16 tests de integración pasando en verde contra contenedor real de Neo4j.
  - `mvn spotless:apply`: Formato 100% limpio.
- **Frontend**:
  - `pnpm test`: 222/222 tests unitarios pasando en verde (46 suites).
  - `pnpm lint`: Sin errores de ESLint.
  - `pnpm build`: Build exitosa de producción en Vite.
