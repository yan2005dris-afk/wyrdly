# Plan de Acción: HU09 — Sistema de Reacciones a Publicaciones

**Proyecto:** Wyrdly Social (`wyrdly-backend` & `wyrdly-frontend`)  
**Módulo:** Backend 2 (Social Graph) / Frontend (Feed Interactions)  
**Referencia:** HU09 / Tarea 10 ([backend-2-tasks.md](file:///home/andy/Escritorio/PROYECTOS/YAGA/yaga-social/docs/tasks/backend-2-tasks.md), [API_CONTRACT.md](file:///home/andy/Escritorio/PROYECTOS/YAGA/yaga-social/docs/api-contracts/API_CONTRACT.md))  
**Estado:** Propuesta técnica y ruta de ejecución  

---

## 1. Resumen Ejecutivo y Alcance

Implementar el sistema de reacciones interactivas para publicaciones en la red social Wyrdly. El sistema permite a usuarios autenticados reaccionar (`LIKE`, `LOVE`, `CELEBRATE`) a cualquier post existente, cambiar su tipo de reacción o retirarla (toggle off), sincronizando el grafo de Neo4j y la interfaz en tiempo real de forma resiliente bajo alta concurrencia.

### Componentes Involucrados
* **`wyrdly-backend`:** Quarkus 3.x (Java 21), Arquitectura Onion ([ADR-002](file:///home/andy/Escritorio/PROYECTOS/YAGA/yaga-social/docs/decisions/ADR-002-backend-onion-layered-architecture.md)), Neo4j Java Driver reactivo.
* **`wyrdly-frontend`:** React 18+ (Vite, TypeScript), Screaming Architecture & FSD ([ADR-004](file:///home/andy/Escritorio/PROYECTOS/YAGA/yaga-social/docs/decisions/ADR-004-frontend-screaming-feature-sliced-architecture.md)), Optimistic UI con rollback.
* **Persistencia:** Neo4j 5.26 Community (Docker `wyrdly-neo4j`).

---

## 2. Métricas y Criterios de Aceptación

### 2.1. Criterios de Aceptación Funcionales (AC)
1. **AC-01 (Toggle / Adición):** Si el usuario no tiene reacción previa sobre el post, al enviar `{"type": "LIKE"}` se crea la relación `(:Usuario)-[:REACCIONA {tipo: 'LIKE', createdAt: datetime()}]->(:Post)`. El servidor retorna status `"ADDED"`, `reactionType: "LIKE"` y el total acumulado de reacciones.
2. **AC-02 (Toggle / Eliminación):** Si el usuario ya tiene reacción `LIKE` sobre el post y vuelve a enviar `{"type": "LIKE"}`, la relación `[:REACCIONA]` se elimina completamente del grafo. El servidor retorna status `"REMOVED"`, `reactionType: null` y el total acumulado decrementado.
3. **AC-03 (Actualización de tipo):** Si el usuario tiene reacción `LIKE` y envía `{"type": "LOVE"}`, la propiedad `tipo` de la relación existente se actualiza a `"LOVE"` y se actualiza `updatedAt: datetime()`. El servidor retorna status `"UPDATED"`, `reactionType: "LOVE"` y el total acumulado sin alterar la cantidad neta de reactores.
4. **AC-04 (Validación de tipos):** Solo se permiten los valores `"LIKE"`, `"LOVE"`, `"CELEBRATE"`. Cualquier otro valor (`"DISLIKE"`, `""`, etc.) retorna `400 Bad Request` con mensaje de validación estandarizado.
5. **AC-05 (Post inexistente):** Si el `postId` no existe en Neo4j, retorna `404 Not Found`.
6. **AC-06 (Autenticación):** El endpoint requiere JWT válido vía encabezado `Authorization: Bearer <token>`. Peticiones anónimas o con token expirado retornan `401 Unauthorized`.
7. **AC-07 (Consistencia con HU08 Feed):** Las reacciones agregadas o removidas deben reflejarse inmediatamente en la consulta del feed `GET /api/feed` (en `reactionCounts` y `userReaction`).

### 2.2. Métricas No Funcionales y SLAs de Rendimiento
| Métrica | Meta / Umbral | Método de Verificación |
|---|---|---|
| **Concurrencia Simultánea** | $\ge 5,000$ usuarios concurrentes | Prueba de carga K6 / JMeter con rampa de subida |
| **Latencia p95 (Carga Normal)** | $< 80\text{ ms}$ | Telemetría / K6 percentiles |
| **Latencia p99 (Pico 5,000 req/s)** | $< 250\text{ ms}$ | Telemetría / K6 percentiles |
| **Tasa de Error HTTP (5xx)** | $< 0.05\%$ | Log monitor & métricas Quarkus (`/q/metrics`) |
| **Recuperación de Deadlocks** | $100\%$ reintentadas con éxito en driver | Sin excepciones no controladas de `TransientException` |
| **Uso de Memoria Neo4j** | Heap $< 80\%$ y cero OOM bajo carga | Docker stats / Neo4j JMX metrics |

---

## 3. Medidas Técnicas para Evitar Quiebre con $\ge 5,000$ Usuarios

### 3.1. Hot Node Lock Contention en Neo4j (Peligro Crítico)
* **Causa Raíz:** En Neo4j, crear, mutar o eliminar una relación `(:Usuario)-[:REACCIONA]->(:Post)` bloquea con cerrojo de escritura exclusivo (`ExclusiveLock`) al nodo `:Post`. Si un post viral recibe miles de reacciones simultáneas, todas las transacciones compiten por bloquear el mismo nodo físico en memoria/disco.
* **Medida 1: Transacción Atómica en Cypher (Single Round-Trip):**
  Evitar lógica distribuida en Java tipo "leer si existe, procesar en memoria, escribir después". Toda la decisión de existencia, toggle y conteo se ejecuta en **una sola consulta Cypher atómica**:
  ```cypher
  MATCH (p:Post {id: $postId})
  WITH p
  MATCH (u:Usuario {id: $userId})
  OPTIONAL MATCH (u)-[r:REACCIONA]->(p)
  WITH p, u, r,
       CASE 
         WHEN r IS NULL THEN 'ADDED'
         WHEN r.tipo = $tipo THEN 'REMOVED'
         ELSE 'UPDATED'
       END AS actionStatus
  FOREACH (_ IN CASE WHEN actionStatus = 'REMOVED' THEN [1] ELSE [] END |
    DELETE r
  )
  FOREACH (_ IN CASE WHEN actionStatus = 'UPDATED' THEN [1] ELSE [] END |
    SET r.tipo = $tipo, r.updatedAt = datetime()
  )
  FOREACH (_ IN CASE WHEN actionStatus = 'ADDED' THEN [1] ELSE [] END |
    CREATE (u)-[:REACCIONA {tipo: $tipo, createdAt: datetime()}]->(p)
  )
  WITH p, actionStatus
  MATCH (p)<-[allR:REACCIONA]-()
  RETURN p.id AS postId,
         actionStatus AS status,
         CASE WHEN actionStatus = 'REMOVED' THEN null ELSE $tipo END AS reactionType,
         count(allR) AS totalReactions
  ```
* **Medida 2: Manejo de Retries Automáticos de Neo4j Driver:**
  Toda escritura debe pasar obligatoriamente por `session.executeWrite(tx -> ...)`. El driver oficial de Neo4j implementa de forma nativa reintentos con backoff exponencial y jitter aleatorio ante `TransientException` (fallos temporales de adquisición de candados de concurrencia).

### 3.2. Dimensionamiento del Pool de Conexiones Bolt y Vert.x
* **Causa Raíz:** El pool Bolt por defecto de Quarkus maneja 100 conexiones. Con 5,000 peticiones concurrentes, la cola de hilos se llena en milisegundos, provocando `ConnectionPoolTimeoutException` o `HTTP 503`.
* **Configuración en `wyrdly-backend/src/main/resources/application.properties`:**
  ```properties
  # Optimización de Conexiones Bolt para Alto Tráfico
  quarkus.neo4j.pool.max-connection-pool-size=300
  quarkus.neo4j.pool.connection-acquisition-timeout=30s
  quarkus.neo4j.pool.idle-time-before-connection-test=60s
  
  # Vert.x Thread Pool para Quarkus REST
  quarkus.thread-pool.core-threads=100
  quarkus.thread-pool.max-threads=500
  quarkus.thread-pool.queue-size=5000
  ```

### 3.3. Configuración de Recursos Docker (`compose.yaml`)
* **Causa Raíz:** Actualmente `wyrdly-neo4j` está configurado con `NEO4J_dbms_memory_heap_max__size=1024m` (1 GB). Con miles de traversals concurrentes ocurrirán pausas largas de recolección de basura (GC pauses) que colgarán el servicio.
* **Ajustes en `compose.yaml`:**
  ```yaml
  environment:
    - NEO4J_dbms_memory_heap_initial__size=1024m
    - NEO4J_dbms_memory_heap_max__size=4096m
    - NEO4J_dbms_memory_pagecache_size=2048m
  ```

### 3.4. Throttling e In-Flight Guards en Frontend (`wyrdly-frontend`)
* **Debounce y Bloqueo de Ráfaga:** En `PostCard.tsx`, prevenir que un usuario envíe 20 peticiones por segundo al hacer click repetido (rage clicks).
* **Control en Hook:** El hook de reacción debe mantener un flag `isReacting[postId] = true`. Si ya hay una petición HTTP en tránsito para ese post, descartar clicks adicionales hasta recibir respuesta o error del backend.
* **Optimistic Update con Rollback Inmediato:** Actualizar visualmente el botón y el contador al instante. Si la petición falla (red o 5xx), revertir el estado y notificar con un toast sutil.

---

## 4. Estrategia de Pruebas y Procedimiento de QA

### 4.1. Pruebas Unitarias (Backend)
1. **`ReactionTypeTest` / `ReactionStatusTest`:** Validar deserialización y rechazo de valores desconocidos.
2. **`ReactPostRequestTest`:** Validar que `@NotBlank` y los validadores de Bean Validation capturen payloads nulos o vacíos.
3. **`PostServiceTest` (o `ReactionServiceTest`):**
   * Mock de `PostRepository` retornando `ReactionResult`.
   * Verificar llamada correcta pasando `userId`, `postId` y `ReactionType`.
   * Verificar propagación de excepciones de negocio (`PostNotFoundException`).
4. **`PostResourceTest`:**
   * Simular JWT válido y probar `POST /api/posts/{postId}/react`.
   * Validar código 200 y estructura JSON exacta.
   * Validar código 400 ante body inválido.
   * Validar código 404 cuando el caso de uso lance `PostNotFoundException`.
   * Validar código 401 cuando no haya header `Authorization`.

### 4.2. Pruebas de Integración (Backend con Neo4j Testcontainers)
En `Neo4jPostRepositoryAdapterIT`:
1. **Flujo completo de Toggle:**
   * Crear Post y Usuario base.
   * Ejecutar reacción `LIKE` $\rightarrow$ Verificar status `ADDED`, total = 1.
   * Ejecutar reacción `LIKE` nuevamente $\rightarrow$ Verificar status `REMOVED`, total = 0, relación eliminada en grafo.
   * Ejecutar reacción `LOVE` $\rightarrow$ Verificar status `ADDED`, total = 1.
   * Ejecutar reacción `CELEBRATE` $\rightarrow$ Verificar status `UPDATED`, total = 1, relación tiene `tipo = 'CELEBRATE'`.
2. **Post Inexistente:**
   * Ejecutar reacción a ID falso $\rightarrow$ Retorna `Optional.empty()` o lanza `PostNotFoundException`.
3. **Test de Concurrencia (Simulación de Carreras):**
   * Lanzar 50 hilos concurrentes (`ExecutorService`) reaccionando simultáneamente al mismo post con usuarios distintos.
   * Validar que al terminar, el conteo total en Neo4j sea exactamente 50 y no haya relaciones duplicadas ni inconsistencias de cerrojo.

### 4.3. Pruebas de Frontend (`wyrdly-frontend`)
1. **`posts.test.ts`:** Probar llamada a `postsApi.react(postId, "LIKE")` verificando URL y método.
2. **`PostCard.test.tsx`:**
   * Simular click en botón de Like.
   * Verificar llamada a callback con parámetros correctos.
   * Verificar que clicks en ráfaga rápida estén bloqueados mientras la mutación está pendiente.
3. **`useFeed.test.ts`:**
   * Validar actualización optimista de reacción.
   * Simular fallo HTTP (500) y verificar que el feed revierta la reacción al estado previo sin desincronizar la UI.

### 4.4. Procedimiento Manual de QA (Checklist de Verificación)

| Paso | Acción de QA | Resultado Esperado |
|---|---|---|
| 1 | Iniciar sesión con Usuario A en `wyrdly-frontend` | Feed carga con publicaciones y botones de reacción |
| 2 | Click en ícono de Corazón (`LIKE`) en un post con 0 likes | Corazón se pinta de rojo (`fill-rose-500`), contador sube a `1`. En DB existe relación `[:REACCIONA {tipo: 'LIKE'}]`. |
| 3 | Click nuevamente en Corazón (`LIKE`) en el mismo post | Corazón vuelve a gris, contador baja a `0`. En DB la relación ya no existe. |
| 4 | Click en Corazón (`LIKE`) con Usuario A; en otra ventana (incógnito) con Usuario B dar `LIKE` | Contador sube a `2`. Ambos usuarios ven su respectivo estado de reacción. |
| 5 | Enviar petición cURL con `{"type": "INVALID"}` | HTTP 400 Bad Request con mensaje explicativo. |
| 6 | Enviar petición cURL a `POST /api/posts/pst_ficticio/react` con token | HTTP 404 Not Found con mensaje "El post no existe". |
| 7 | Enviar petición cURL sin token | HTTP 401 Unauthorized. |
| 8 | Refrescar la página del feed | El estado de la reacción y los conteos persisten idénticos desde Neo4j. |

---

## 5. Fases de Ejecución Paso a Paso

```
[Fase 0: Saneamiento de Base]
  ├── Corregir error de compilación en PostRepository (findByAuthor)
  └── Corregir error de compilación en Neo4jPostRepositoryAdapter (mapRecordToPost)

[Fase 1: Capa de Dominio en wyrdly-backend]
  ├── Crear com.wyrdly.post.domain.model.ReactionType (LIKE, LOVE, CELEBRATE)
  ├── Crear com.wyrdly.post.domain.model.ReactionStatus (ADDED, REMOVED, UPDATED)
  ├── Crear com.wyrdly.post.domain.model.ReactionResult (record con postId, tipo, status, total)
  ├── Crear com.wyrdly.post.domain.exception.PostNotFoundException
  └── Extender PostRepository con reactToPost(...)

[Fase 2: Capa de Aplicación en wyrdly-backend]
  ├── Crear ReactPostRequest (DTO con validaciones Bean Validation)
  ├── Crear ReactPostResponse (DTO con estructura de API_CONTRACT)
  ├── Crear ReactToPostUseCase (interfaz)
  └── Implementar ReactionService o enriquecer PostService con la orquestación

[Fase 3: Infraestructura y Persistencia en wyrdly-backend]
  ├── Implementar consulta Cypher atómica en Neo4jPostRepositoryAdapter
  ├── Manejo de Optional/PostNotFoundException si el nodo :Post no existe
  └── Pruebas de integración Neo4jPostRepositoryAdapterIT

[Fase 4: Interfaces REST en wyrdly-backend]
  ├── Agregar endpoint POST /api/posts/{postId}/react en PostResource
  ├── Registrar PostNotFoundExceptionMapper (HTTP 404)
  └── Ejecutar suite completa de tests de wyrdly-backend (100% pasando)

[Fase 5: Integración en wyrdly-frontend]
  ├── Agregar postsApi.react(...) en wyrdly-frontend/src/api/posts.ts
  ├── Conectar mutación con optimistic update y rollback en useFeed / FeedPage
  ├── Proteger contra doble click (in-flight guard / debounce)
  └── Validar tests de frontend con npm test

[Fase 6: Tuning de Concurrencia y Verificación]
  ├── Ajustar parámetros de pool y threads en application.properties
  ├── Ajustar memoria de Neo4j en compose.yaml
  └── Ejecutar procedimiento de QA funcional
```
