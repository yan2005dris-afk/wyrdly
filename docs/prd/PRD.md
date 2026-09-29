# Documento de Requisitos del Producto (PRD)
## Red Social Distribuida

---

## 1. Resumen Ejecutivo y Objetivos
La Red Social Distribuida es una aplicación web diseñada para poner en práctica y validar conceptos fundamentales de **Sistemas Distribuidos**. El proyecto hace énfasis en la separación estricta de responsabilidades, capas de persistencia especializadas (grafos y almacenamiento de objetos), mensajería bidireccional en tiempo real y mecanismos de notificación push desacoplados.

### Objetivos Principales
- Permitir el registro, autenticación y gestión de perfiles de usuario.
- Generar un feed de publicaciones personalizado basado estrictamente en recorridos del grafo social.
- Implementar mensajería directa e instantánea entre usuarios mediante WebSockets persistentes (sin sondeo HTTP / polling).
- Configurar notificaciones push en segundo plano utilizando el estándar Web Push (VAPID) ante eventos sociales (nuevas publicaciones de seguidos).
- Proveer recomendaciones de usuarios no triviales a partir del análisis topológico del grafo.

---

## 2. Roles y Audiencia Objetivo
- **Usuario Autenticado:** Persona registrada que publica contenido multimedia, sigue a otros usuarios, reacciona a publicaciones, chatea en tiempo real y recibe notificaciones push.
- **Visitante Anónimo:** Usuario no autenticado que accede a las pantallas de bienvenida, inicio de sesión y registro.

---

## 3. Matriz de Características, Protocolos y Estimación

| Característica (CAR) | Unidad de Trabajo (HU / Tarea) | Endpoint / Protocolo | Códigos HTTP / Eventos | Estimación (hh) |
|---|---|---|---|---|
| **CAR1: Identidad** | HU01 / Tarea 01 (Registro) | `POST /api/auth/register` | `201`, `400`, `409` | 8 hh |
| **CAR1: Identidad** | HU02 / Tarea 02 (Login JWT) | `POST /api/auth/login` | `200`, `400`, `401` | 8 hh |
| **CAR1: Identidad** | HU03 / Tarea 03 (Perfil) | `GET/PUT /api/users/...` | `200`, `401`, `404` | 6 hh |
| **CAR2: Grafo Social** | HU04 / Tarea 04 (Seguir/Dejar) | `POST/DELETE /api/users/{id}/follow` | `200`, `400`, `401`, `404`, `409` | 6 hh |
| **CAR2: Grafo Social** | HU05 / Tarea 05 (Seguidores) | `GET /api/users/{id}/followers` | `200`, `404` | 6 hh |
| **CAR2: Grafo Social** | HU06 / Tarea 06 (Recomendaciones) | `GET /api/users/suggestions` | `200`, `401` | 10 hh |
| **CAR3: Multimedia** | HU07 / Tarea 07 (Upload S3) | `POST /api/media/upload` | `201`, `400`, `401`, `413` | 10 hh |
| **CAR3: Multimedia** | HU08 / Tarea 08 (Crear Post) | `POST /api/posts` | `201`, `400`, `401` | 8 hh |
| **CAR4: Feed** | HU09 / Tarea 09 (Feed Grafo) | `GET /api/feed` | `200`, `401` | 10 hh |
| **CAR5: Reacciones** | HU10 / Tarea 10 (Reaccionar) | `POST /api/posts/{id}/react` | `200`, `400`, `401`, `404` | 6 hh |
| **CAR6: Chat Realtime** | HU11 / Tarea 11 (Handshake WS) | `ws://.../ws/chat?token=...` | `101 Switching`, `4401 Unauth` | 12 hh |
| **CAR6: Chat Realtime** | HU12 / Tarea 12 (Mensajería) | WebSocket Frames | `SEND_MESSAGE`, `NEW_MESSAGE` | 14 hh |
| **CAR6: Chat Realtime** | HU13 / Tarea 13 (Historial) | `GET /api/chat/{userId}/history`| `200`, `401`, `404` | 10 hh |
| **CAR7: Web Push** | HU14 / Tarea 14 (Suscripción) | `POST /api/notifications/subscribe`| `200`, `400`, `401` | 8 hh |
| **CAR7: Web Push** | HU15 / Tarea 15 (Emisión Push) | RFC 8030 / VAPID Dispatch | `200`, `201`, `410 Gone` | 12 hh |
| **CAR8: Infra & QA** | HU16 / Tarea 16 (Docker) | `docker compose up` | `Healthcheck OK (0)` | 10 hh |
| **CAR8: Infra & QA** | HU17 / Tarea 17 (Cypher QA) | Bolt Protocol / Cypher Shell | `Query Execution OK` | 10 hh |

---

## 4. Fichas Detalladas de Historias de Usuario y Criterios de Aceptación

### HU01: Registro de Usuarios
- **Referencia:** HU01
- **Nombre:** Registro de nueva cuenta de usuario
- **Endpoint:** `POST /api/auth/register`
- **Descripción:** Permite a un visitante crear una cuenta en la red social proporcionando sus datos básicos. La contraseña se hashea con BCrypt y se crea el nodo `:Usuario` en Neo4j con restricciones de unicidad.
- **Actor:** Visitante anónimo
- **Prioridad:** Alta | **Riesgo:** Medio | **Estimación:** 8 hh
- **Respuestas HTTP Esperadas:**
  - `201 Created`: Usuario creado exitosamente. Devuelve el objeto de usuario público sin contraseña.
  - `400 Bad Request`: Parámetros incompletos, formato de email inválido o contraseña menor a 8 caracteres.
  - `409 Conflict`: El `username` o `email` ya se encuentra registrado en el sistema.
- **Criterios de Aceptación (Calidad):**
  1. *Dado* un payload JSON con `username`, `email`, `password`, `fullName` y `bio` válidos, *cuando* se envía a `POST /api/auth/register`, *entonces* el sistema crea el nodo `:Usuario`, cifra la contraseña con BCrypt y responde `201 Created`.
  2. *Dado* un `username` ya existente, *cuando* se intenta registrar nuevamente, *entonces* el sistema responde `409 Conflict` con mensaje `"El nombre de usuario ya está en uso"`.
  3. La contraseña **nunca** debe ser persistida en texto plano ni retornada en ninguna respuesta JSON.

---

### HU02: Inicio de Sesión y Autenticación JWT
- **Referencia:** HU02
- **Nombre:** Autenticación de usuario y emisión de token JWT
- **Endpoint:** `POST /api/auth/login`
- **Descripción:** Valida las credenciales ingresadas contra el hash en Neo4j y emite un token JWT firmado para autenticar subsecuentes llamadas REST y el handshake de WebSocket.
- **Actor:** Usuario registrado
- **Prioridad:** Alta | **Riesgo:** Alto | **Estimación:** 8 hh
- **Respuestas HTTP Esperadas:**
  - `200 OK`: Credenciales válidas. Devuelve `{ "token": "...", "user": { ... } }`.
  - `400 Bad Request`: Payload sin usuario o contraseña.
  - `401 Unauthorized`: Contraseña incorrecta o usuario inexistente.
- **Criterios de Aceptación (Calidad):**
  1. *Dado* un usuario registrado, *cuando* envía su `username` y `password` correctos, *entonces* recibe `200 OK` con un JWT que contiene los claims `sub` (userId), `username` y fecha de expiración (`exp`).
  2. *Dado* un intento con contraseña errónea, *cuando* se envía la solicitud, *entonces* responde `401 Unauthorized` sin revelar si el error fue de usuario o de contraseña.
  3. El token debe expirar según la configuración del sistema (ej. 24 horas).

---

### HU03: Consulta y Edición de Perfil de Usuario
- **Referencia:** HU03
- **Nombre:** Gestión y visualización de perfil
- **Endpoints:** `GET /api/users/{username}` (Público) | `PUT /api/users/profile` (Protegido)
- **Descripción:** Permite ver el perfil con contadores calculados en el grafo (`followersCount`, `followingCount`, `isFollowing`) y editar información personal.
- **Actor:** Usuario
- **Prioridad:** Media | **Riesgo:** Bajo | **Estimación:** 6 hh
- **Respuestas HTTP Esperadas:**
  - `200 OK`: Perfil consultado o actualizado exitosamente.
  - `401 Unauthorized`: Solicitud `PUT` sin token JWT válido.
  - `404 Not Found`: El `username` consultado no existe en Neo4j.
- **Criterios de Aceptación (Calidad):**
  1. *Dado* un `GET /api/users/{username}`, *entonces* el backend ejecuta una consulta Cypher que calcula en tiempo real `count((:Usuario)-[:SIGUE]->(u))` y `count((u)-[:SIGUE]->(:Usuario))`.
  2. *Dado* un usuario autenticado que envía `PUT /api/users/profile`, *entonces* solo puede modificar su propio nombre, biografía y avatarUrl, respondiendo `200 OK`.

---

### HU04: Seguir y Dejar de Seguir Usuarios
- **Referencia:** HU04
- **Nombre:** Creación y eliminación de relaciones de seguimiento
- **Endpoints:** `POST /api/users/{targetUserId}/follow` | `DELETE /api/users/{targetUserId}/follow`
- **Descripción:** Crea o destruye la relación dirigida `(:Usuario)-[:SIGUE]->(:Usuario)` en Neo4j.
- **Actor:** Usuario autenticado
- **Prioridad:** Alta | **Riesgo:** Medio | **Estimación:** 6 hh
- **Respuestas HTTP Esperadas:**
  - `200 OK`: Relación de seguimiento creada o eliminada exitosamente.
  - `400 Bad Request`: El `targetUserId` es el mismo que el usuario autenticado (auto-seguimiento prohibido).
  - `401 Unauthorized`: Token JWT ausente o inválido.
  - `404 Not Found`: El `targetUserId` no existe en la base de datos.
- **Criterios de Aceptación (Calidad):**
  1. *Dado* un usuario A autenticado y un usuario B existente, *cuando* A ejecuta `POST /api/users/{B}/follow`, *entonces* se ejecuta un `MERGE (a)-[r:SIGUE]->(b)` con propiedad `createdAt` y responde `200 OK`.
  2. *Dado* que A intenta seguir a A mismo, *entonces* responde `400 Bad Request` con mensaje `"No puedes seguirte a ti mismo"`.
  3. *Dado* que A ejecuta `DELETE /api/users/{B}/follow`, *entonces* la relación `[:SIGUE]` es eliminada del grafo.

---

### HU05: Sugerencias Inteligentes de Amistad (Grafo Multi-salto) *(No Prioritario)*
- **Referencia:** HU05
- **Nombre:** Recomendación de conexiones basada en el grafo social
- **Endpoint:** `GET /api/users/suggestions`
- **Descripción:** Obtiene recomendaciones de usuarios a 2 saltos de distancia (`(me)-[:SIGUE]->(amigo)-[:SIGUE]->(sugerido)`), excluyendo usuarios ya seguidos y a uno mismo, ordenados por amigos en común.
- **Actor:** Usuario autenticado
- **Prioridad:** Media | **Riesgo:** Alto | **Estimación:** 10 hh
- **Respuestas HTTP Esperadas:**
  - `200 OK`: Lista de usuarios recomendados con puntaje (`score`) y nombres de amigos en común.
  - `401 Unauthorized`: Token JWT ausente o inválido.
- **Criterios de Aceptación (Calidad):**
  1. La consulta **no puede ser aleatoria**. Debe justificar la relación en el grafo.
  2. Si el usuario no sigue a nadie aún, el sistema debe tener un fallback basado en usuarios con mayor centralidad/seguidores de la red.
  3. Tiempo de respuesta menor a 200 ms para grafos estándar.

---

### HU06: Subida de Archivos Multimedia a RustFS (S3)
*(Nota de diseño: El servicio de subida puede invocarse de manera desacoplada o integrarse de forma compuesta durante el flujo de creación de publicaciones y fotos de perfil).*
- **Referencia:** HU06
- **Nombre:** Desacoplamiento y carga de binarios en Object Storage
- **Endpoint:** `POST /api/media/upload` (Multipart/form-data)
- **Descripción:** Recibe imágenes/archivos, valida tamaño y tipo MIME, los almacena en el bucket de RustFS y devuelve la URL pública/identificador de almacenamiento.
- **Actor:** Usuario autenticado
- **Prioridad:** Alta | **Riesgo:** Medio | **Estimación:** 10 hh
- **Respuestas HTTP Esperadas:**
  - `201 Created`: Archivo subido exitosamente a RustFS. Devuelve `{ "fileUrl": "...", "storageKey": "..." }`.
  - `400 Bad Request`: Archivo corrupto o tipo MIME no permitido (solo imágenes/videos permitidos).
  - `401 Unauthorized`: Token JWT ausente o inválido.
  - `413 Payload Too Large`: Archivo excede el límite configurado (ej. 10 MB).
- **Criterios de Aceptación (Calidad):**
  1. El archivo binario **jamás se almacena en Neo4j**.
  2. El archivo queda accesible vía HTTP a través de la URL de RustFS generada.

---

### HU07: Creación y Publicación de Posts
- **Referencia:** HU07
- **Nombre:** Publicación de contenido en el grafo
- **Endpoint:** `POST /api/posts`
- **Descripción:** Crea el nodo `:Post` y lo conecta con el autor mediante `(:Usuario)-[:PUBLICA {createdAt}]->(:Post)` en Neo4j. La notificación a seguidores se delega a HU11 (Tareas 11.1–11.4), que provee el módulo de Web Push; ver [issue #32](https://github.com/yan2005dris-afk/wyrdly/issues/32).
- **Actor:** Usuario autenticado
- **Prioridad:** Alta | **Riesgo:** Medio | **Estimación:** 8 hh
- **Respuestas HTTP Esperadas:**
  - `201 Created`: Post publicado y persistido en Neo4j.
  - `400 Bad Request`: Contenido de texto vacío o URL multimedia inválida.
  - `401 Unauthorized`: Token JWT ausente o inválido.
- **Criterios de Aceptación (Calidad):**
  1. *Dado* un post válido con o sin imagen, *cuando* se crea, *entonces* queda indexado por `createdAt` en Neo4j.
  2. *Dado* un autor con seguidores registrados, *cuando* se publique HU11 (Web Push), *entonces* PostService deberá disparar el evento de notificación a cada seguidor activo. **Fuera del alcance de HU07 actual.**

---

### HU08: Generación de Feed por Recorrido de Grafo
- **Referencia:** HU08
- **Nombre:** Consulta del feed personalizado relacional
- **Endpoint:** `GET /api/feed?page=0&limit=20`
- **Descripción:** Devuelve las publicaciones en orden cronológico inverso creadas **únicamente por los usuarios que el usuario actual sigue** en Neo4j.
- **Actor:** Usuario autenticado
- **Prioridad:** Alta | **Riesgo:** Alto | **Estimación:** 10 hh
- **Respuestas HTTP Esperadas:**
  - `200 OK`: Arreglo de publicaciones con datos del autor, reacciones agregadas y estado de reacción del usuario.
  - `401 Unauthorized`: Token JWT ausente o inválido.
- **Criterios de Aceptación (Calidad):**
  1. Queda **estrictamente prohibido** devolver un volcado global tipo `MATCH (p:Post) RETURN p`.
  2. La consulta debe utilizar la ruta: `(me:Usuario)-[:SIGUE]->(author:Usuario)-[:PUBLICA]->(post:Post)`.
  3. Soporta paginación eficiente con `SKIP` y `LIMIT`.

---

### HU09: Reacciones a Publicaciones
- **Referencia:** HU09
- **Nombre:** Registro de reacciones interactivas
- **Endpoint:** `POST /api/posts/{postId}/react`
- **Descripción:** Crea, actualiza o elimina (toggle) la relación `(:Usuario)-[:REACCIONA {tipo, fecha}]->(:Post)`.
- **Actor:** Usuario autenticado
- **Prioridad:** Media | **Riesgo:** Bajo | **Estimación:** 6 hh
- **Respuestas HTTP Esperadas:**
  - `200 OK`: Reacción agregada, actualizada o eliminada. Devuelve estado actual y total de reacciones.
  - `400 Bad Request`: Tipo de reacción no soportado.
  - `401 Unauthorized`: Token JWT ausente o inválido.
  - `404 Not Found`: El `postId` no existe en Neo4j.
- **Criterios de Aceptación (Calidad):**
  1. Si el usuario ya reaccionó con "LIKE" y vuelve a enviar "LIKE", la relación se elimina (toggle off).
  2. Si envía un tipo distinto, la propiedad `tipo` se actualiza.

---

### HU10: Chat en Tiempo Real vía WebSocket
- **Referencia:** HU10
- **Nombre:** Mensajería instantánea bidireccional
- **Protocolo / Endpoint:** `ws://localhost:8080/ws/chat?token=<JWT>`
- **Descripción:** Canal TCP persistente para el intercambio de mensajes 1 a 1 en tiempo real con persistencia en Neo4j (`:Mensaje`). Prohibido el uso de polling HTTP.
- **Actor:** Usuario autenticado
- **Prioridad:** Alta | **Riesgo:** Alto | **Estimación:** 14 hh
- **Respuestas / Códigos de Control WebSocket:**
  - `101 Switching Protocols`: Handshake exitoso, sesión registrada en el pool de conexiones.
  - `4401 Unauthorized (Close Code)`: Token JWT inválido o expirado al intentar conectar.
  - `Event NEW_MESSAGE`: Mensaje entrante entregado instantáneamente al destinatario conectado.
- **Criterios de Aceptación (Calidad):**
  1. La latencia de retransmisión del mensaje entre dos clientes debe ser inferior a 100 ms.
  2. Todo mensaje enviado debe persistirse en el nodo `(:Mensaje)` con relaciones `(:Usuario)-[:ENVIA]->(:Mensaje)-[:DIRIGIDO_A]->(:Usuario)`.
  3. Si el destinatario está desconectado, el mensaje se persiste para que lo lea al conectarse.

---

### HU11: Notificaciones Web Push Desacopladas (VAPID)
- **Referencia:** HU11
- **Nombre:** Alertas push nativas del navegador en segundo plano
- **Endpoints:** `GET /api/notifications/vapid-public-key` | `POST /api/notifications/subscribe`
- **Descripción:** Envía notificaciones del estándar W3C Push API / RFC 8030 firmadas con VAPID a los dispositivos de los seguidores cuando un usuario publica.
- **Actor:** Sistema / Usuario seguidor
- **Prioridad:** Alta | **Riesgo:** Alto | **Estimación:** 12 hh
- **Respuestas HTTP Esperadas:**
  - `200 OK`: Suscripción registrada exitosamente en el nodo `:Usuario`.
  - `400 Bad Request`: Objeto de suscripción sin `endpoint` o claves criptográficas `p256dh`/`auth`.
  - `410 Gone (del Push Service)`: Suscripción expirada o revocada por el cliente, el backend debe limpiar las claves.
- **Criterios de Aceptación (Calidad):**
  1. La notificación debe recibirse incluso con la pestaña del navegador cerrada o minimizada.
  2. No se considera válido simular notificaciones mediante un `alert()` o `toast` interno de React mientras la app está abierta.
  3. Al hacer clic en la notificación del SO, el Service Worker debe abrir la URL de la publicación.

---

## 5. Plan de Aseguramiento de Calidad y Criterios de Aceptación Globales

| Dimensión de Calidad | Criterio de Aceptación Global | Método de Verificación |
|---|---|---|
| **Seguridad** | 100% de endpoints privados rechazan peticiones sin JWT con código `401`. | Pruebas automatizadas REST con HTTPie / JUnit. |
| **Separación de Almacenamiento** | Cero bytes binarios almacenados en Neo4j. | Auditoría de propiedades de nodos `:Post` en Neo4j Browser. |
| **Tiempo Real** | Cero consultas HTTP repetitivas (polling) en la consola de red del navegador para el chat. | Inspección de pestaña Network (filtrado por WS/XHR). |
| **Consultas Cypher** | Al menos 5 consultas complejas documentadas y ejecutadas con éxito, incluyendo una multinivel. | Ejecución de scripts Cypher en Neo4j Browser. |
| **Reproducibilidad** | `docker compose up --build` levanta Neo4j, RustFS, Backend y Frontend sin intervención manual. | Prueba de levantamiento en entorno limpio. |

---

## 6. Distribución de Responsabilidades del Equipo (4 Integrantes)

```text
┌──────────────────────────────────────────────┐
│        Líder de Proyecto y Arquitecto        │  -> TRD, Esquema Neo4j, Docker Compose e Integración
└──────────────────────┬───────────────────────┘
                       │
       ┌───────────────┼───────────────────────────────┐
       ▼               ▼                               ▼
┌──────────────┐ ┌──────────────┐               ┌──────────────┐
│  Backend 1   │ │  Backend 2   │               │   Frontend   │
│  (Auth, Post,│ │  (Grafo,     │               │  (React SPA, │
│  S3 y Push)  │ │  Chat WS,    │               │   Estado y   │
│              │ │  Cypher)     │               │  ServWorker) │
└──────────────┘ └──────────────┘               └──────────────┘
```

1. **Integrante 1 (Líder de Proyecto, Arquitectura e Integración):**
   - Orquestación de infraestructura Docker Compose, definición del modelo Neo4j, pruebas de integración extremo a extremo y defensa de la arquitectura.
2. **Integrante 2 (Backend Core - Dominio y Almacenamiento):**
   - API REST en Quarkus: Autenticación (JWT), Gestión de Usuarios, Publicaciones, Cliente S3/RustFS y Servicio Web Push (VAPID).
3. **Integrante 3 (Backend Tiempo Real y Especialista en Grafos):**
   - Endpoints WebSocket en Quarkus (gestión de sesiones activas y persistencia de chat), operaciones de seguimiento, feed relacional y las 5+ consultas Cypher no triviales.
4. **Integrante 4 (Frontend Lead - React SPA y Service Worker):**
   - Interfaz gráfica en React, vistas de Feed, Perfil y Publicación, subida de archivos multimedia, cliente WebSocket de Chat y registro de Service Worker para Web Push.
