# Plan de Acción: HU09 — Sistema de Reacciones a Publicaciones

**Proyecto:** Wyrdly Social (`wyrdly-backend` & `wyrdly-frontend`)
**Módulo:** Backend 2 (Social Graph) / Frontend (Feed Interactions)
**Referencia:** HU09 / Tarea 10 ([backend-2-tasks.md](../tasks/backend-2-tasks.md), [API_CONTRACT.md](../api-contracts/API_CONTRACT.md))
**Estado:** Diseño técnico consolidado — listo para implementación
**Versión:** 2.1 (alineado HU01-HU08: relación directa, sin nodo intermedio, 5 fases optimizadas)

---

## 1. Resumen Ejecutivo y Alcance

Implementar el sistema de reacciones interactivas (`LIKE`, `LOVE`, `CELEBRATE`) sobre publicaciones. El sistema soporta toggle (mismo tipo = remover, tipo distinto = actualizar), es seguro bajo concurrencia masiva (≥ 5.000 usuarios concurrentes), idempotente frente a doble click, y observable extremo a extremo.

### Componentes Involucrados
* **`wyrdly-backend`:** Quarkus 3.x, Java 21, Arquitectura Onion ([ADR-002](../decisions/ADR-002-backend-onion-layered-architecture.md)), Neo4j Java Driver reactivo, SmallRye Fault Tolerance + Metrics.
* **`wyrdly-frontend`:** React 18 + TypeScript (Vite), Screaming Architecture & FSD ([ADR-004](../decisions/ADR-004-frontend-screaming-feature-sliced-architecture.md)), TanStack Query v5 para mutaciones.
* **Persistencia:** Neo4j 5.26 Community (Docker `wyrdly-neo4j`).
* **Cache / Rate-limit:** Redis 7 (contenedor existente o añadir a `compose.yaml`).

---

## 2. Pre-requisitos y Asunciones

Antes de Fase 0, el repositorio debe cumplir:

1. **`mvn -pl wyrdly-backend clean verify` pasa en `develop` o rama base.** Si falla, se aborta HU09.
2. **Dependencies en `pom.xml` presentes** (Fase 0 validará):
   - `quarkus-redis-client` (idempotencia + rate-limit cache)
   - `quarkus-smallrye-fault-tolerance` (circuit breaker + retry)
   - `quarkus-micrometer-registry-prometheus` (@Timed + métricas)
   - `bucket4j` (rate-limit bucket algorithm)
3. **Redis en `compose.yaml`:** agregar `redis:7-alpine` si falta (Fase 0).
4. **`messages.properties`** en `wyrdly-backend/src/main/resources/`: debe existir para códigos de error (Fase 0).
5. **Constraints únicos Neo4j creados** (Fase 0, validar con `SHOW CONSTRAINTS`):
   ```cypher
   CREATE CONSTRAINT post_id_unique IF NOT EXISTS FOR (p:Post) REQUIRE p.id IS UNIQUE;
   CREATE CONSTRAINT usuario_id_unique IF NOT EXISTS FOR (u:Usuario) REQUIRE u.id IS UNIQUE;
   ```
6. **Legacy reactions existentes** (`[:LIKE|:LOVE|:CELEBRATE]`): Fase 2 los migrará a `[:REACCIONA {tipo}]` (decisión: deprecate legacy, usar solo REACCIONA).
7. **`Post` y `Usuario` exponen propiedad `id` tipo `String`** (no `elementId`).

---

## 3. Métricas y Criterios de Aceptación

### 3.1. Criterios de Aceptación Funcionales (AC)

| ID | Comportamiento | Resultado esperado |
|---|---|---|
| **AC-01** | Sin reacción previa, `POST /api/posts/{postId}/react` con `{"type":"LIKE"}` | Crea `(:Usuario)-[:REACCIONA {tipo:'LIKE', createdAt, updatedAt}]->(:Post)`. Status `ADDED`. `reactionsCount` += 1. |
| **AC-02** | Con reacción previa `LIKE`, mismo payload | Elimina la relación. Status `REMOVED`. `reactionsCount` -= 1. `userReaction = null`. |
| **AC-03** | Con reacción previa `LIKE`, payload `{"type":"LOVE"}` | Actualiza `r.tipo = 'LOVE'`, `r.updatedAt`. Status `UPDATED`. `reactionsCount` sin cambio. |
| **AC-04** | `type` fuera de `{LIKE, LOVE, CELEBRATE}` o `null`/vacío | `400 Bad Request` con `code: "VALIDATION_ERROR"` y `details: [{field:"type", ...}]` (RFC 7807 problem+json o formato actual del API_CONTRACT). |
| **AC-05** | `postId` inexistente | `404 Not Found` con `code: "POST_NOT_FOUND"`. |
| **AC-06** | Sin JWT o JWT expirado | `401 Unauthorized` con `code: "UNAUTHENTICATED"`. |
| **AC-07** | Reacción agregada/removida debe verse en `GET /api/feed` siguiente | `reactionCounts` y `userReaction` reflejan estado en lectura inmediata (≤ 100 ms). |
| **AC-08** | Doble click idéntico en < 200 ms | El servidor entrega el mismo estado final lógico (no dos toggles consecutivos). Respuesta puede ser la del primer request o estado final consolidado. |

### 3.2. Métricas No Funcionales (NFR) y SLAs

| Métrica | Meta | Método de verificación |
|---|---|---|
| Concurrencia simultánea | ≥ 5.000 usuarios | K6 script versionado en `loadtest/hu09-reactions.k6.js`, ramp 0→5000 VU en 2 min |
| Latencia p95 (carga normal) | < 80 ms | Histogram Prometheus `reaction_duration_seconds` |
| Latencia p99 (pico 5.000 req/s) | < 250 ms | Mismo histograma, percentiles vía Grafana |
| Tasa de error 5xx | < 0.05% | Counter `reaction_errors_total{reason}` |
| Recuperación de `TransientException` | 100% reintentos exitosos | Sin excepciones no manejadas; retry del driver con backoff exponencial |
| Heap Neo4j | < 80% bajo carga | Docker stats + JMX Prometheus |
| Disponibilidad del endpoint | ≥ 99.9% mensual | Probe HTTP externa |

---

## 4. Modelo de Datos (Grafo)

```mermaid
graph LR
  U[(:Usuario {id, username, ...})]
  P[(:Post {id, content, createdAt, ...})]

  U -- "REACCIONA {tipo, createdAt, updatedAt}" --> P

  style U fill:#dbeafe,stroke:#0284c7
  style P fill:#fce7f3,stroke:#be185d
```

**Decisión:** la reacción se modela como **relación directa** `(:Usuario)-[:REACCIONA {tipo, createdAt, updatedAt}]->(:Post)`. Razones:
1. **Alineado HU08:** Feed query ya cuenta relaciones directas `[:LIKE|:LOVE|:CELEBRATE]`. Nodo intermedio requeriría refactoring disruptivo de queries existentes.
2. **Simplicidad:** sin traversales adicionales para lectura. Cypher más legible.
3. **Rendimiento:** relación directa = O(1) lookup de reacción de usuario en feed query.
4. **Consistencia:** sigue patrón `:Usuario-[:PUBLICA]->:Post` y `:Usuario-[:SIGUE]->:Usuario` ya implementados en HU01-HU08.

**Alternativa descartada:** nodo intermedio `(:Usuario)-[:TIENE_REACCION]->(:Reaccion)-[:A_POST]->(:Post)`. Teórico pero rompe queries HU08, requiere migración completa de feed.

---

## 5. Decisiones de Arquitectura Clave

### 5.0. Decisión Crítica: Deprecate Legacy Reactions, Usar Solo [:REACCIONA]

**Contexto:** HU01-HU08 usan `[:LIKE|:LOVE|:CELEBRATE]` (relaciones directas legacy). HU09 introduce `[:REACCIONA {tipo}]`. Feed query tiene TODO: extender para ambas.

**Decisión (Opción B — Recomendada):** 
- Migrar todos los `[:LIKE|:LOVE|:CELEBRATE]` existentes a `[:REACCIONA {tipo}]` en Fase 2.
- Feed query: contar **solo** `[:REACCIONA]`, eliminar legacy OPTIONAL MATCH.
- Cypher migration (idempotente):
  ```cypher
  MATCH (u:Usuario)-[r:LIKE|LOVE|CELEBRATE]->(p:Post)
  CREATE (u)-[r_new:REACCIONA {tipo: type(r), createdAt: r.createdAt, updatedAt: datetime()}]->(p)
  DELETE r
  ```

**Consecuencias:**
* (+) Queries más simples: una sola relación, no bifurcación legacy/new.
* (+) Feed query O(1) sin lógica condicional.
* (+) Superficie de ataque reducida (un modelo, no dos).
* (−) Migración de datos (pequeña si dataset seeded).
* (−) Legacy readers (si existen) verán cambio (mitigado: no hay readers externos).

**Alternativas descartadas:**
* Opción A (legacy forever): Queries cada vez más complejas. ❌
* Opción C (dual-count): Confuso, mantenimiento overhead. ❌

**Validación:** Antes de Fase 2, verificar con `MATCH (:Usuario)-[r:LIKE|LOVE|CELEBRATE]->() RETURN count(r)` cuántos existen. Si > 0, ejecutar migration (toma < 1 seg típicamente).

---

### 5.1. Idempotencia de doble click (REST layer)

**Mecanismo:** dedup en Redis con clave `react:{userId}:{postId}` y TTL 250 ms (implementado en `RateLimitFilter`). Si el segundo request llega dentro de la ventana, se sirve la respuesta cacheada del primero. Si llega después, se procesa normalmente como toggle.

**Ubicación:** JAX-RS filter o guarda en `PostResource.react()` antes de llamar al use case.

### 5.2. Rate-limit por usuario (REST layer)

**Mecanismo:** filtro JAX-RS custom `ReactionRateLimitFilter` con bucket4j-redis. Política: 30 reacciones / minuto / usuario, configurable vía `wyrdly.reactions.rate-limit.capacity=30` y `wyrdly.reactions.rate-limit.window-seconds=60`. Excedido → `429 Too Many Requests` con `Retry-After` header.

**Ubicación:** `@Provider ReactionRateLimitFilter implements ContainerRequestFilter` registrado en `wyrdly-backend/src/main/java/com/wyrdly/post/interfaces/rest/filters/`. Aplica sólo a `POST /api/posts/{postId}/react`.

> **Nota:** SmallRye Fault Tolerance **no** provee `@RateLimiting`. Sólo tiene `@Bulkhead` (semáforo, no ventana temporal). Por eso usamos bucket4j en un filtro explícito.

### 5.3. Circuit breaker

`@CircuitBreaker(requestVolumeThreshold=20, failureRatio=0.5, delay=10s)` en `PostResource.react()`. Fallback: retorna `503 SERVICE_UNAVAILABLE` con `code: "DEPENDENCY_DOWN"` y mensaje al frontend para mostrar toast de "Servicio temporalmente no disponible, reintenta".

### 5.4. Observabilidad obligatoria

* **Métricas Prometheus** (vía `/q/metrics`):
  * `reaction_duration_seconds` (histogram, labels: `outcome` ∈ {added, removed, updated, error})
  * `reaction_errors_total{reason}` (counter, labels: `reason` ∈ {validation, not_found, dependency_down, transient})
  * `reaction_idempotency_hits_total` (counter)
  * `reaction_rate_limited_total` (counter)
* **Tracing distribuido:** OpenTelemetry auto-instrumentado por Quarkus. Span `POST /api/posts/{id}/react` con atributos `userId`, `postId`, `outcome`.
* **Logs estructurados:** JSON con `correlation_id`, `userId`, `postId`, `outcome`, `latency_ms`.

---

## 6. Diseño Backend

### 6.1. Contrato de API

`POST /api/posts/{postId}/react`

**Headers:**
* `Authorization: Bearer <jwt>` (obligatorio)
* `Content-Type: application/json`
* `Idempotency-Key: <uuid>` (opcional, recomendado)

**Body:**
```json
{ "type": "LIKE" | "LOVE" | "CELEBRATE" }
```

**Respuestas:**

| Status | Body | Cuándo |
|---|---|---|
| 200 OK | `{"status":"ADDED","reactionType":"LIKE","totalReactions":42}` | Reacción creada |
| 200 OK | `{"status":"REMOVED","reactionType":null,"totalReactions":41}` | Reacción eliminada |
| 200 OK | `{"status":"UPDATED","reactionType":"LOVE","totalReactions":42}` | Reacción cambiada |
| 400 | RFC 7807 problem detail | Body inválido |
| 401 | RFC 7807 problem detail | Sin JWT |
| 404 | RFC 7807 problem detail | Post no existe |
| 429 | RFC 7807 problem detail + `Retry-After` | Rate-limit excedido |
| 503 | RFC 7807 problem detail | Circuit breaker abierto |

### 6.2. Capa de Dominio

```
com.wyrdly.post.domain.model
├── ReactionType    (enum: LIKE, LOVE, CELEBRATE)
├── ReactionStatus  (enum: ADDED, REMOVED, UPDATED)
├── ReactionResult  (record: postId, status, reactionType, totalReactions)
└── PostNotFoundException (extends DomainException)
```

### 6.3. Capa de Aplicación

`ReactToPostUseCase` (interfaz en `application/port/in/`):
```java
ReactionResult react(String userId, String postId, ReactionType type);
```

`ReactionService` (implementación en `application/service/`):
1. Recibe `userId` (extraído de `JsonWebToken.getSubject()` en REST layer).
2. Llama a `PostRepository.react(userId, postId, type)` (transacción única Neo4j).
3. Mapea resultado a DTO, retorna (sin guardia de rate-limit ni idempotencia aquí).

**Nota:** Rate-limit e idempotencia se aplican en REST layer (§6.5), anterior al service.

### 6.4. Capa de Infraestructura — Query Cypher atómica

Una sola transacción `executeWrite`, operación toggle sobre relación directa:

```cypher
// Detección de estado + mutación atómica
MATCH (u:Usuario {id: $userId})
MATCH (p:Post {id: $postId})
OPTIONAL MATCH (u)-[existing:REACCIONA]->(p)

WITH u, p, existing,
     CASE
       WHEN existing IS NULL              THEN 'ADDED'
       WHEN existing.tipo = $tipo         THEN 'REMOVED'
       ELSE                                    'UPDATED'
     END AS status

// Remover relación (toggle idéntico)
FOREACH (_ IN CASE WHEN status = 'REMOVED' THEN [1] ELSE [] END |
  DELETE existing
)

// Actualizar relación (cambiar tipo + updatedAt)
FOREACH (_ IN CASE WHEN status = 'UPDATED' THEN [1] ELSE [] END |
  SET existing.tipo = $tipo,
      existing.updatedAt = datetime()
)

// Crear relación (nuevo tipo)
FOREACH (_ IN CASE WHEN status = 'ADDED' THEN [1] ELSE [] END |
  CREATE (u)-[r:REACCIONA {tipo: $tipo, createdAt: datetime(), updatedAt: datetime()}]->(p)
)

RETURN status,
       CASE WHEN status = 'REMOVED' THEN null ELSE $tipo END AS reactionType
```

**Notas críticas:**
* Todo en una sola transacción → un solo `ExclusiveLock` sobre `:Post` por request.
* Relación directa: sin nodo intermedio, sin contador denormalizado (feed query ya cuenta vía aggregation).
* `status = 'UPDATED'` no requiere conteo adicional (relación existe, solo cambia propriedad).
* Si el `:Post` no existe, el `MATCH` retorna 0 filas → el caso de uso mapea a `PostNotFoundException` → 404.
* **Post-Fase 2 (post-migración):** HU08 feed query actualizada para contar solo `[:REACCIONA]`, legacy `:LIKE|:LOVE|:CELEBRATE` eliminadas. Cypher simplificado (ver §5.0).

### 6.5. REST Resource: PostResource.react()

```java
@POST
@Path("/{postId}/react")
@Authenticated
@Timed(name = "reaction_duration_seconds", ...)
@CircuitBreaker(requestVolumeThreshold = 20, failureRatio = 0.5, delay = 10, delayUnit = ChronoUnit.SECONDS)
public Response react(@PathParam String postId, ReactPostRequest req, @Context SecurityContext sec) {
  // userId siempre desde JWT, nunca del body
  String userId = sec.getUserPrincipal().getName();

  // Idempotencia y rate-limit aplicados via filtros JAX-RS
  // (ReactionIdempotencyFilter + ReactionRateLimitFilter) — ver §5.1 y §5.2.

  // Ejecutar use case
  ReactionResult result = reactToPostUseCase.react(userId, postId, req.getType());
  ReactPostResponse resp = mapToResponse(result);

  // Cache 250 ms para dedup idempotente (filter también escribe)
  redisCache.setex("react:" + userId + ":" + postId, 250, resp);

  // Métricas
  recordMetrics(resp);

  return Response.ok(resp).build();
}
```

**Configuración de recursos (`application.properties`)**
```properties
# Pool Bolt
quarkus.neo4j.pool.max-connection-pool-size=300
quarkus.neo4j.pool.connection-acquisition-timeout=30s
quarkus.neo4j.pool.idle-time-before-connection-test=60s

# Thread pool Vert.x (REST)
quarkus.thread-pool.core-threads=100
quarkus.thread-pool.max-threads=500
quarkus.thread-pool.queue-size=5000

# Redis (idempotencia + rate-limit)
quarkus.redis.hosts=redis://localhost:6379

# Métricas + tracing
quarkus.micrometer.enabled=true
quarkus.micrometer.export.prometheus.enabled=true
quarkus.otel.enabled=true
quarkus.otel.service.name=wyrdly-backend
```

### 6.6. Manejo de retries del driver

Toda escritura: `session.executeWrite(tx -> tx.run(cypher, params).single())`. El driver Neo4j reintenta automáticamente `TransientException` con backoff exponencial + jitter. No capturar `TransientException` en código de aplicación; dejar que el driver la maneje. Sí capturar `ServiceUnavailableException` para circuit breaker.

---

## 7. Diseño Frontend

### 7.1. Stack de mutación: Integración minimal (sin nueva lib)

**Nota:** Frontend actual usa `useState/useEffect` manual (useFeed.ts). Se integra `useReaction` como hook simple que envuelve axios + AbortController para dedup in-flight. **NO añadimos TanStack Query** a package.json.

### 7.2. Hook `useReaction`

```typescript
// wyrdly-frontend/src/features/feed/hooks/useReaction.ts
export function useReaction(postId: string) {
  const [isPending, setIsPending] = useState(false);
  const [feed, setFeed] = useState<Post[]>([]);
  const abortControllerRef = useRef<AbortController | null>(null);

  const react = async (type: ReactionType) => {
    if (isPending) return; // Dedup in-flight

    setIsPending(true);
    abortControllerRef.current = new AbortController();

    try {
      // Optimistic update
      setFeed((old) =>
        old.map((post) =>
          post.id === postId
            ? {
                ...post,
                userReaction: type,
                reactionCounts: {
                  ...post.reactionCounts,
                  // Adjust counts based on old userReaction
                },
              }
            : post,
        ),
      );

      const response = await postsApi.react(
        postId,
        type,
        abortControllerRef.current.signal,
      );

      // Server response: apply actual state
      setFeed((old) =>
        old.map((post) =>
          post.id === postId
            ? {
                ...post,
                userReaction: response.reactionType,
                reactionCounts: { LIKE: response.totalReactions }, // Adjust per response
              }
            : post,
        ),
      );
    } catch (err) {
      if (!(err instanceof DOMException && err.name === 'AbortError')) {
        // Rollback on error
        setFeed((old) => [...old]); // Re-fetch or previous state
        toast.error('No se pudo registrar la reacción. Intenta de nuevo.');
      }
    } finally {
      setIsPending(false);
    }
  };

  return { react, isPending };
}
```

**Dedup:** AbortController cancela requests in-flight si un nuevo click llega. Redis dedup server-side (250ms) maneja doble-click cross-tab.

### 7.3. Componente `PostCard` (3 botones)

```typescript
// wyrdly-frontend/src/features/feed/components/PostCard.tsx
export function PostCard({ post }) {
  const { react, isPending } = useReaction(post.id);

  return (
    <div className="post-card">
      <p>{post.content}</p>

      {/* Reaction buttons (LIKE, LOVE, CELEBRATE) */}
      <div className="reaction-buttons">
        {(['LIKE', 'LOVE', 'CELEBRATE'] as const).map((type) => (
          <button
            key={type}
            onClick={() => react(type)}
            disabled={isPending}
            aria-pressed={post.userReaction === type}
            className={`reaction-btn ${
              post.userReaction === type ? 'active' : ''
            } ${isPending ? 'opacity-60 cursor-wait' : ''}`}
          >
            {type} ({post.reactionCounts[type] || 0})
          </button>
        ))}
      </div>
    </div>
  );
}
```

**UI States:**
* `isPending === true` → todos los botones deshabilitados, cursor wait
* `userReaction === type` → botón con clase `active` (relleno/destacado)
* Click → dedup in-flight, optimistic update, esperar respuesta

### 7.4. Capa API

`wyrdly-frontend/src/shared/api/posts.ts`:
```typescript
export const postsApi = {
  react: async (
    postId: string,
    type: ReactionType,
    signal?: AbortSignal,
  ): Promise<ReactPostResponse> => {
    const { data } = await apiClient.post<ReactPostResponse>(
      `/api/posts/${postId}/react`,
      { type },
      {
        headers: { 'Idempotency-Key': crypto.randomUUID() },
        signal, // AbortController signal
      },
    );
    return data;
  },
};
```

---

## 8. Configuración de Infraestructura (Neo4j)

### 8.1. `compose.yaml` — Dev

```yaml
services:
  wyrdly-neo4j:
    image: neo4j:5.26-community
    environment:
      - NEO4J_AUTH=neo4j/dev_password_change_me
      - NEO4J_dbms_memory_heap_initial__size=1G
      - NEO4J_dbms_memory_heap_max__size=2G
      - NEO4J_dbms_memory_pagecache_size=1G
      - NEO4J_dbms_security_procedures_unrestricted=apoc.*
      - NEO4J_server_metrics_enabled=true
      - NEO4J_server_metrics_prometheus_enabled=true
      - NEO4J_server_metrics_prometheus_endpoint=0.0.0.0:2004
    ports:
      - "7474:7474"
      - "7687:7687"
      - "2004:2004"   # Prometheus scraping
    volumes:
      - neo4j-data:/data
      - ./neo4j/migrations:/var/lib/neo4j/import/migrations
```

### 8.2. Producción (referencia para deploy)

| Recurso | Heap | Pagecache | Conexiones Bolt |
|---|---|---|---|
| Dev (este compose) | 2 GB | 1 GB | 100 |
| Staging | 4 GB | 4 GB | 200 |
| Prod (5.000+ concurrent) | 8 GB | 8 GB | 300+ |

Monitorear `wyrdly-neo4j_heap_used_ratio` y `wyrdly-neo4j_pagecache_evictions`. Alertar si heap > 80%.

---

## 9. Estrategia de Pruebas

### 9.1. Unit (Backend)

* `ReactionTypeTest`: deserialización JSON, rechazo de valores fuera del enum.
* `ReactPostRequestTest`: `@NotNull`, `@Pattern` capturan payloads inválidos.
* `ReactionServiceTest` con mocks:
  * Verifica llamada a `PostRepository.react(userId, postId, type)`.
  * Verifica propagación de `PostNotFoundException` → 404.
  * Verifica propagación de `RateLimitExceededException` → 429.
  * Verifica que resultado del repo se mapea 1:1 a DTO.
* `PostResourceTest`:
  * 200 con JWT válido para ADDED/REMOVED/UPDATED.
  * 400 con body inválido.
  * 404 cuando use case lanza `PostNotFoundException`.
  * 401 sin `Authorization`.
  * 429 cuando rate-limit dispara.
  * 503 cuando circuit breaker abierto.

### 9.2. Integración (Backend + Neo4j Testcontainers)

`Neo4jPostReactionRepositoryIT`:
1. **Setup:** Flyway/Liquibase ejecuta migrations (incluye constraints únicos).
2. **Toggle completo:** LIKE → LIKE (REMOVED) → LOVE (ADDED) → CELEBRATE (UPDATED). Verificar relación [:REACCIONA] existe/se elimina/cambia tipo correctamente.
3. **Post inexistente:** `Optional.empty()` o excepción de dominio (`PostNotFoundException`).
4. **Validación de tipos:** payload inválido rechazado en REST layer antes de tocar DB.
5. **Concurrencia controlada:** 50 hilos, mismo post, usuarios distintos. Verificar 50 relaciones [:REACCIONA] creadas (una por usuario, sin duplicados).
6. **Feed integration:** Después de crear reacciones, llamar a `findFeedByUserId()` y verificar que `userReaction` y conteos sean correctos.
7. **Driver retries:** mock que inyecta `TransientException` en primera llamada, éxito en segunda → test verifica que la transacción se completa.

### 9.3. Carga (K6 — versionado en repo)

`loadtest/hu09-reactions.k6.js`:
* Ramp: 0 → 1000 → 3000 → 5000 VU en 2 min.
* Cada VU: 80% reads (feed), 20% reactions (toggle LIKE).
* Thresholds: `http_req_duration{name:react}: p(95)<80`, `p(99)<250`, `http_req_failed: rate<0.0005`.
* Ejecutar en CI nightly y antes de release.

### 9.4. Frontend

* `useReaction.test.ts`: 
  * Mockear `postsApi.react`
  * Click → `react(type)` llamada, `isPending = true`
  * Optimistic update aplica al estado local
  * Respuesta servidor aplica estado final
  * Error → rollback + toast
  * Click doble (isPending=true) → dedup in-flight (segundo click ignorado)
* `PostCard.test.tsx`: 
  * 3 botones (LIKE/LOVE/CELEBRATE) renderizados
  * Click → `react(type)` llamada
  * `isPending=true` → botones deshabilitados, opacity-60
  * `userReaction === type` → clase `active` aplicada
* `FeedPage.test.tsx`: reacción optimista visible al instante; rollback al fallar.

### 9.5. QA Manual (Checklist)

| # | Acción | Esperado |
|---|---|---|
| 1 | Login Usuario A, abrir feed | Botones de reacción visibles, contadores en 0 |
| 2 | Click LIKE en post propio | UI actualiza al instante, contador +1, ícono relleno |
| 3 | Click LIKE de nuevo | UI revierte, contador -1, ícono vacío |
| 4 | LIKE con A, en otra sesión LIKE con B | Contador = 2, ambos ven su estado |
| 5 | LIKE con A, cambiar a LOVE | UI muestra LOVE, contador sin cambio |
| 6 | Click rápido 10 veces en 1s | Una sola mutación final, sin parpadeo errático |
| 7 | `curl` con `{"type":"INVALID"}` | 400 con `code: "VALIDATION_ERROR"` |
| 8 | `curl POST /api/posts/pst_fake/react` con token válido | 404 con `code: "POST_NOT_FOUND"` |
| 9 | `curl` sin `Authorization` | 401 con `code: "UNAUTHENTICATED"` |
| 10 | Apagar Neo4j, intentar reacción | 503 con toast "Servicio no disponible" |
| 11 | Refrescar feed | Estado persiste desde Neo4j, sin desfase |

---

## 10. Fases de Ejecución (5 fases optimizadas)

```
[Fase 0 — Pre-requisitos]  (bloqueante: si falla, no iniciar Fase 1)
  ├── git rev-parse HEAD del branch base
  ├── mvn -pl wyrdly-backend clean verify (debe pasar 100%)
  ├── npm run build en wyrdly-frontend (debe pasar)
  ├── Validar pom.xml: quarkus-redis-client, quarkus-smallrye-fault-tolerance, quarkus-micrometer-registry-prometheus, bucket4j (agregar si faltan)
  ├── Agregar redis:7-alpine a compose.yaml (si no existe)
  ├── Crear wyrdly-backend/src/main/resources/messages.properties (si no existe)
  ├── Levantar compose, verificar Neo4j + Redis + logging
  ├── MATCH (:Usuario)-[r:LIKE|LOVE|CELEBRATE]->() RETURN count(r) — contar legacy reactions
  └── Crear constraints únicos Post.id y Usuario.id (si no existen)

[Fase 1 — Capa de Dominio]
  ├── ReactionType (enum: LIKE, LOVE, CELEBRATE)
  ├── ReactionStatus (enum: ADDED, REMOVED, UPDATED)
  ├── ReactionResult (record: status, reactionType, postId)
  └── PostNotFoundException (extends DomainException)

[Fase 2 — Infraestructura + Migración Legacy]
  ├── **Migración de Legacy Reactions** (Cypher idempotente, §5.0):
  │   └── V<n>__migrate_legacy_reactions_to_reacciona.cypher
  │       • MATCH (:Usuario)-[r:LIKE|LOVE|CELEBRATE]->(:Post)
  │       • CREATE [:REACCIONA {tipo, createdAt, updatedAt}]
  │       • DELETE legacy r
  │       • Validar post-ejecución: count legacy = 0, count REACCIONA ≥ count pre-migración
  ├── PostRepository.react(userId, postId, type) → ReactionResult (nueva firma)
  ├── Neo4jPostRepositoryAdapter.react() — Cypher toggle relación directa (§6.4)
  ├── Neo4jPostReactionRepositoryIT — Testcontainers, toggle ADDED/REMOVED/UPDATED + concurrencia 50
  └── Actualizar HU08 FEED_QUERY: contar solo [:REACCIONA], eliminar legacy OPTIONAL MATCH (feed más simple post-migración)

[Fase 3 — Aplicación + REST]
  ├── ReactPostRequest (DTO + @NotNull @Pattern)
  ├── ReactPostResponse (DTO: status, reactionType, totalReactions)
  ├── ReactToPostUseCase (interfaz port-in)
  ├── ReactionService (implementación, orquestación simple)
  ├── POST /api/posts/{postId}/react en PostResource (§6.5)
  ├── @RateLimiting (30/min/usuario)
  ├── @CircuitBreaker (requestVolumeThreshold=20, delay=10s)
  ├── Redis dedup 250 ms para idempotencia
  ├── ExceptionMappers: PostNotFoundException→404, RateLimitException→429, CircuitBreakerOpen→503
  ├── @Timed para reaction_duration_seconds
  └── Tests: ReactionServiceTest (mocks), PostResourceTest (200/400/401/404/429/503)

[Fase 4 — Frontend + Observabilidad]
  ├── shared/api/posts.ts → postsApi.react(postId, type)
  ├── features/feed/hooks/useReaction.ts (TanStack Query mutation)
  ├── features/feed/components/PostCard.tsx (click handler + isPending UI)
  ├── Toast para errores transitorios + retry
  ├── Tests: useReaction.test, PostCard.test, FeedPage.test
  ├── Prometheus metrics (reaction_duration_seconds, reaction_errors_total)
  ├── Dashboard Grafana: p95/p99/error rate/heap Neo4j
  └── Alertas Alertmanager: p95>80ms, error>0.05%, heap>80%

[Fase 5 — Carga + QA]
  ├── loadtest/hu09-reactions.k6.js (ramp 0→5000 VU, 80% feed 20% react)
  ├── Thresholds K6: p95<80ms, p99<250ms, error<0.0005
  ├── Ejecutar carga en CI (nightly o pre-merge)
  ├── Checklist manual §9.5 (dos navegadores, edge cases)
  ├── Firma QA + release notes
  └── Tag vX.Y.Z, actualizar API_CONTRACT.md, merge a develop
```

---

## 11. Riesgos y Mitigaciones (resumen)

| Riesgo | Probabilidad | Impacto | Mitigación |
|---|---|---|---|
| Migración legacy → REACCIONA pierde reacciones | Baja | Alto | Cypher idempotente con CREATE antes de DELETE, validar count pre/post (§5.0, Fase 2) |
| Hot node lock en `:Post` bajo viral | Media | Alto | Query atómica con transacción única (§6.4), relación directa sin nodo intermedio |
| Rage-click satura driver | Alta | Medio | Dedup Redis 250 ms (§5.1) + rate-limit 30/min (§5.2) |
| Neo4j cae → peticiones se apilan | Baja | Alto | Circuit breaker + fallback 503 (§5.3) |
| SLA no verificable sin métricas | Alta | Alto | Métricas Prometheus obligatorias desde Fase 4 (§5.4) |
| Token JWT con `userId` falsificado | Baja | Crítico | `userId` siempre desde `JsonWebToken.getSubject()` en REST, nunca del body |
| Redis no disponible → idempotencia + rate-limit fallan | Baja | Medio | Validar Redis en Fase 0, healthcheck en compose, fallback graceful si falta |

---

## 12. Checklist de Salida (Definition of Done)

**Fase 0:**
- [ ] pom.xml contiene: quarkus-redis-client, quarkus-smallrye-fault-tolerance, quarkus-micrometer-registry-prometheus, bucket4j
- [ ] compose.yaml contiene redis:7-alpine con healthcheck
- [ ] `wyrdly-backend/src/main/resources/messages.properties` existe con códigos: `VALIDATION_ERROR`, `POST_NOT_FOUND`, `UNAUTHENTICATED`, `RATE_LIMITED`, `DEPENDENCY_DOWN`
- [ ] Neo4j constraints verificados: Post.id, Usuario.id (SHOW CONSTRAINTS)
- [ ] `mvn clean verify` pasa 100% en rama base

**Fase 1-3:**
- [ ] `mvn verify` pasa al 100% (unit + integration + Testcontainers)
- [ ] PostNotFoundException creada (nueva excepción de dominio)
- [ ] Migration Cypher V<n>__migrate_legacy_reactions_to_reacciona.cypher ejecutada y validada (count legacy post-ejecución = 0)
- [ ] HU08 FEED_QUERY actualizado: contar solo [:REACCIONA], legacy OPTIONAL MATCH removidos
- [ ] RateLimitFilter JAX-RS custom implementado con bucket4j-redis
- [ ] ReactionRateLimitFilter aplicado solo a POST /api/posts/{postId}/react
- [ ] Circuit breaker @CircuitBreaker en PostResource.react() con fallback 503

**Fase 4-5:**
- [ ] `npm test` y `npm run build` pasan
- [ ] useReaction hook implementado (axios + AbortController, sin TanStack Query)
- [ ] PostCard con 3 botones (LIKE/LOVE/CELEBRATE) renderizados
- [ ] K6 loadtest cumple thresholds: p95<80ms, p99<250ms, error<0.05% (adjuntar log en PR)
- [ ] Métricas Prometheus expuestas (`/q/metrics`): `reaction_duration_seconds`, `reaction_errors_total`, `reaction_rate_limited_total`
- [ ] Dashboard Grafana configurado con p95/p99/error rate/heap Neo4j
- [ ] API_CONTRACT.md actualizado con POST /api/posts/{postId}/react (respuestas 200/400/401/404/429/503)
- [ ] QA manual §9.5 completado y firmado en PR
- [ ] Merge a `develop`, `git tag vX.Y.Z`, release notes actualizadas
