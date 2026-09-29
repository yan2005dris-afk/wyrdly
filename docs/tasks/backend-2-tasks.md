# Tareas de Desarrollo: Backend 2 (Neo4j Graph & WebSockets)
**Módulo:** Backend 2  
**Revisión:** Tech Lead

---

## 📌 Inicialización de Neo4j (Constraints & Índices)
- [x] **Tarea 0.1:** Crear script/servicio de arranque en Quarkus para ejecutar las restricciones de unicidad en Neo4j:
  - `CREATE CONSTRAINT FOR (u:Usuario) REQUIRE u.id IS UNIQUE;`
  - `CREATE CONSTRAINT FOR (u:Usuario) REQUIRE u.username IS UNIQUE;`
  - `CREATE CONSTRAINT FOR (u:Usuario) REQUIRE u.email IS UNIQUE;`
  - `CREATE CONSTRAINT FOR (p:Post) REQUIRE p.id IS UNIQUE;`
  - `CREATE CONSTRAINT FOR (m:Mensaje) REQUIRE m.id IS UNIQUE;`

---

## 📌 HU03: Consulta y Actualización de Perfiles
- [x] **Tarea 3.1:** Implementar consulta Cypher para obtener perfil con conteo agregado de seguidores y seguidos:
  - Contar `(u)<-[:SIGUE]-()` como `followersCount`.
  - Contar `(u)-[:SIGUE]->()` como `followingCount`.
  - Determinar si el usuario autenticado sigue al perfil (`isFollowing`).
- [x] **Tarea 3.2:** Crear endpoint `GET /api/users/{username}` (Público o con contexto JWT opcional).
- [x] **Tarea 3.3:** Crear endpoint `PUT /api/users/profile` para actualizar `fullName`, `bio` o `avatarUrl`.

---

## 📌 HU04: Sistema de Seguimiento (Follow / Unfollow)
- [ ] **Tarea 4.1:** Implementar consulta Cypher `MERGE (a)-[r:SIGUE {fecha: datetime()}]->(b)` con validación de no auto-seguimiento (`a.id <> b.id`).
- [ ] **Tarea 4.2:** Crear endpoint `POST /api/users/{targetUserId}/follow`.
- [ ] **Tarea 4.3:** Implementar consulta Cypher `MATCH (a)-[r:SIGUE]->(b) DELETE r`.
- [ ] **Tarea 4.4:** Crear endpoint `DELETE /api/users/{targetUserId}/follow`.

---

## 📌 HU05: Sugerencias Inteligentes de Amistad (Grafo)
- [x] **Tarea 5.1:** Diseñar consulta Cypher multi-hop (amigos de amigos):
  - `MATCH (yo:Usuario {id: $userId})-[:SIGUE]->(amigo:Usuario)-[:SIGUE]->(sugerido:Usuario)`
  - Excluir usuarios que ya sigo y a mí mismo: `WHERE NOT (yo)-[:SIGUE]->(sugerido) AND sugerido <> yo`.
  - Agrupar con `COUNT(amigo) AS mutualFriendsCount`, ordenar descendente.
- [x] **Tarea 5.2:** Crear endpoint `GET /api/users/suggestions?page=1&pageSize=10` con formato envelope `data` y `meta`.

---

## 📌 HU08: Generación de Feed por Recorrido de Grafo
- [ ] **Tarea 8.1:** Implementar consulta Cypher optimizada para feed cronológico paginado:
  - `MATCH (yo:Usuario {id: $userId})-[:SIGUE]->(amigo:Usuario)-[:PUBLICA]->(post:Post)`
  - Opcional: incluir publicaciones propias.
  - Retornar posts con agregación de conteo de reacciones (`LIKE`, `LOVE`, `CELEBRATE`) y la reacción propia del usuario.
- [ ] **Tarea 8.2:** Crear endpoint `GET /api/feed?page=1&pageSize=20` con sobre `data` y `meta`.

---

## 📌 HU09: Reacciones a Publicaciones
- [ ] **Tarea 9.1:** Implementar lógica de toggle / actualización de reacción en Cypher:
  - `MERGE (u:Usuario)-[r:REACCIONA]->(p:Post)` actualizando `{tipo: $tipo, fecha: datetime()}` o eliminando si es la misma (toggle).
- [ ] **Tarea 9.2:** Crear endpoint `POST /api/posts/{postId}/react` retornando estado `ADDED`, `REMOVED` o `UPDATED` y el total acumulado.

---

## 📌 HU10: Chat Bidireccional en Tiempo Real (WebSockets & Neo4j)
- [ ] **Tarea 10.1:** Crear endpoint `@ServerEndpoint("/ws/chat")` en Quarkus con soporte de autenticación JWT en el handshake (`?token=...`).
- [ ] **Tarea 10.2:** Mantener mapa en memoria de sesiones activas concurrentes (`ConcurrentHashMap<String, Session>`).
- [ ] **Tarea 10.3:** Manejar eventos entrantes:
  - `SEND_MESSAGE`: Persistir nodo `:Mensaje` con relaciones `(:Usuario)-[:ENVIA]->(:Mensaje)-[:DIRIGIDO_A]->(:Usuario)` y reenviar vía WS al destinatario si está conectado.
  - `TYPING`: Reenviar evento de escritura al destinatario en tiempo real.
- [ ] **Tarea 10.4:** Crear endpoint REST `GET /api/chat/{recipientId}/history?page=1&pageSize=50` con paginación `data`/`meta`.
