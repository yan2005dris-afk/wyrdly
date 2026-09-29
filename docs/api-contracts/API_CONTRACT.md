# Especificación de Interfaces y Contratos de Comunicación (API)
## Desglose por Historias de Usuario (HU01 - HU11)

Este documento constituye el contrato formal de comunicación entre los módulos de **Frontend (React)**, **Backend (Quarkus)** y la capa de persistencia **(Neo4j / RustFS)**, organizado explícitamente por Historia de Usuario.

---

## Estándar Global de Encabezados y Respuestas

### Encabezados Requeridos (Rutas Protegidas)
```http
Authorization: Bearer <TOKEN_JWT>
Content-Type: application/json
Accept: application/json
```

### Formato Estándar de Errores JSON
```json
{
  "timestamp": "2026-09-24T18:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Descripción detallada del error de validación o negocio",
  "path": "/api/..."
}
```

### Formato Estándar de Paginación para Listados y Colecciones
Para todos los endpoints que retornen múltiples elementos (colecciones o consultas generales), se emplea un sobre (`envelope`) estándar compuesto por `data` (arreglo de elementos) y `meta` (objeto de metadatos de navegación):

```json
{
  "data": [
    { /* Objeto del recurso */ }
  ],
  "meta": {
    "page": 1,
    "pageSize": 10,
    "totalElements": 45,
    "totalPages": 5,
    "hasNext": true
  }
}
```
*Nota: Los endpoints de detalle o recurso específico (ej. `GET /api/posts/{id}` o `GET /api/users/{username}`) retornan el objeto de forma directa sin envoltorio.*

---

## HU01: Registro de Usuarios
- **Historia de Usuario:** HU01 / Tarea 01
- **Responsables:** Backend 1 (Auth/Neo4j) & Frontend (Auth Form)
- **Método y Ruta:** `POST /api/auth/register`
- **Autenticación:** Pública (Sin JWT)

### Esquema de la Petición (Request Body)
```json
{
  "username": "juanperez",             // String, requerido, 3-20 caracteres alfanuméricos
  "email": "juanperez@example.com",     // String, requerido, formato email válido
  "password": "PasswordSeguro123!",    // String, requerido, min 8 caracteres
  "fullName": "Juan Pérez",            // String, requerido, 2-100 caracteres
  "bio": "Entusiasta de Grafos"        // String, opcional, max 250 caracteres
}
```

### Respuestas del Servidor (Responses)
- **`201 Created` (Éxito):**
```json
{
  "id": "usr_7f8a9b1c",
  "username": "juanperez",
  "email": "juanperez@example.com",
  "fullName": "Juan Pérez",
  "bio": "Entusiasta de Grafos",
  "avatarUrl": null,
  "createdAt": "2026-09-24T18:30:00Z"
}
```
- **`400 Bad Request` (Validación Fallida):**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "La contraseña debe tener al menos 8 caracteres y contener números y letras.",
  "path": "/api/auth/register"
}
```
- **`409 Conflict` (Duplicado):**
```json
{
  "status": 409,
  "error": "Conflict",
  "message": "El nombre de usuario 'juanperez' ya está registrado.",
  "path": "/api/auth/register"
}
```

---

## HU02: Inicio de Sesión y Autenticación JWT
- **Historia de Usuario:** HU02 / Tarea 02
- **Responsables:** Backend 1 (JWT) & Frontend (Login State / Context)
- **Método y Ruta:** `POST /api/auth/login`
- **Autenticación:** Pública (Sin JWT)

### Esquema de la Petición (Request Body)
```json
{
  "username": "juanperez",             // String, requerido
  "password": "PasswordSeguro123!"     // String, requerido
}
```

### Respuestas del Servidor (Responses)
- **`200 OK` (Autenticado):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c3JfN2Y4YTliMWMiLCJ1c2VybmFtZSI6Imp1YW5wZXJleiIsImV4cCI6MTc1ODczOTAwMH0...",
  "user": {
    "id": "usr_7f8a9b1c",
    "username": "juanperez",
    "fullName": "Juan Pérez",
    "avatarUrl": null
  }
}
```
- **`400 Bad Request`:**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "El usuario y contraseña son obligatorios.",
  "path": "/api/auth/login"
}
```
- **`401 Unauthorized` (Credenciales Inválidas):**
```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Credenciales inválidas.",
  "path": "/api/auth/login"
}
```

---

## HU03: Consulta y Edición de Perfil de Usuario
- **Historia de Usuario:** HU03 / Tarea 03
- **Responsables:** Backend 1 & Frontend (Profile Page)

### Sub-interfaz 3.1: Obtener Perfil Público
- **Método y Ruta:** `GET /api/users/{username}`
- **Autenticación:** Opcional (Si viene JWT, calcula `isFollowing`)
- **`200 OK`:**
```json
{
  "id": "usr_7f8a9b1c",
  "username": "juanperez",
  "fullName": "Juan Pérez",
  "bio": "Entusiasta de Grafos",
  "avatarUrl": "http://localhost:9000/social-media-assets/avatars/juanperez.png",
  "followersCount": 42,
  "followingCount": 18,
  "isFollowing": false,
  "createdAt": "2026-09-24T18:30:00Z"
}
```
- **`404 Not Found`:**
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "El usuario 'carlos99' no existe.",
  "path": "/api/users/carlos99"
}
```

### Sub-interfaz 3.2: Actualizar Perfil
- **Método y Ruta:** `PUT /api/users/profile`
- **Autenticación:** Obligatoria (JWT)
- **Request Body:**
```json
{
  "fullName": "Juan Carlos Pérez",     // String, opcional
  "bio": "Especialista en Neo4j y RustFS", // String, opcional
  "avatarUrl": "http://localhost:9000/social-media-assets/avatars/nuevo.png" // String, opcional
}
```
- **`200 OK`:** Retorna el objeto de usuario actualizado.
- **`401 Unauthorized`:** Token ausente o expirado.

---

## HU04: Seguir y Dejar de Seguir Usuarios
- **Historia de Usuario:** HU04 / Tarea 04
- **Responsables:** Backend 2 (Cypher Relaciones) & Frontend (Follow Buttons)

### Sub-interfaz 4.1: Seguir Usuario
- **Método y Ruta:** `POST /api/users/{targetUserId}/follow`
- **Autenticación:** Obligatoria (JWT)
- **`200 OK`:**
```json
{
  "message": "Usuario seguido exitosamente.",
  "targetUserId": "usr_99a8b7c6",
  "following": true
}
```
- **`400 Bad Request`:**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "No puedes seguirte a ti mismo.",
  "path": "/api/users/usr_7f8a9b1c/follow"
}
```
- **`404 Not Found`:** El `targetUserId` no existe en Neo4j.

### Sub-interfaz 4.2: Dejar de Seguir Usuario
- **Método y Ruta:** `DELETE /api/users/{targetUserId}/follow`
- **Autenticación:** Obligatoria (JWT)
- **`200 OK`:**
```json
{
  "message": "Se dejó de seguir al usuario.",
  "targetUserId": "usr_99a8b7c6",
  "following": false
}
```

---

## HU05: Sugerencias Inteligentes de Amistad (Grafo)
- **Historia de Usuario:** HU05 & HU06 / Tarea 05 y 06
- **Responsables:** Backend 2 (Cypher Multi-hop) & Frontend (Widget Sugerencias)
- **Método y Ruta:** `GET /api/users/suggestions?page=1&pageSize=10`
- **Autenticación:** Obligatoria (JWT)

### Respuestas del Servidor (Responses)
- **`200 OK`:**
```json
{
  "data": [
    {
      "user": {
        "id": "usr_44556677",
        "username": "carlos",
        "fullName": "Carlos Benítez",
        "avatarUrl": "http://localhost:9000/social-media-assets/avatars/carlos.jpg"
      },
      "recommendationScore": 3,
      "mutualFriendsCount": 3,
      "sampleMutualFriends": ["alicia", "roberto", "daniel"]
    }
  ],
  "meta": {
    "page": 1,
    "pageSize": 10,
    "totalElements": 8,
    "totalPages": 1,
    "hasNext": false
  }
}
```
- **`401 Unauthorized`:** Token ausente o expirado.

---

## HU06: Subida de Multimedia a RustFS (S3)
- **Historia de Usuario:** HU06 / Tarea 07
- **Responsables:** Backend 1 (S3 Client) & Frontend (File Picker)
- **Método y Ruta:** `POST /api/media/upload`
- **Tipo de Contenido:** `multipart/form-data`
- **Autenticación:** Obligatoria (JWT)

### Petición (FormData)
- Campo `file`: Archivo binario (Formatos permitidos: `image/jpeg`, `image/png`, `image/webp`, `video/mp4`).

### Respuestas del Servidor (Responses)
- **`201 Created`:**
```json
{
  "fileUrl": "http://localhost:9000/social-media-assets/posts/img_550e8400.jpg",
  "storageKey": "posts/img_550e8400.jpg",
  "mimeType": "image/jpeg",
  "fileSizeBytes": 2048576,
  "uploadedAt": "2026-09-24T19:00:00Z"
}
```
- **`400 Bad Request`:**
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Tipo de archivo no permitido. Solo se aceptan imágenes y videos.",
  "path": "/api/media/upload"
}
```
- **`413 Payload Too Large`:** El archivo supera los 10 MB.

---

## HU07: Creación y Publicación de Posts
- **Historia de Usuario:** HU07 / Tarea 08
- **Responsables:** Backend 1 (Posts/Neo4j) & Frontend (Post Creator)
- **Método y Ruta:** `POST /api/posts`
- **Autenticación:** Obligatoria (JWT)

### Esquema de la Petición (Request Body)
```json
{
  "content": "¡Probando el cluster distribuido con RustFS y Neo4j!", // String, requerido, 1-1000 caracteres
  "mediaUrl": "http://localhost:9000/social-media-assets/posts/img_550e8400.jpg" // String, opcional
}
```

### Respuestas del Servidor (Responses)
- **`201 Created`:**
```json
{
  "id": "pst_12345678",
  "content": "¡Probando el cluster distribuido con RustFS y Neo4j!",
  "mediaUrl": "http://localhost:9000/social-media-assets/posts/img_550e8400.jpg",
  "createdAt": "2026-09-24T19:00:00Z",
  "author": {
    "id": "usr_7f8a9b1c",
    "username": "juanperez",
    "fullName": "Juan Pérez",
    "avatarUrl": null
  }
}
```
  *Nota: `reactionCounts` y `userReaction` se agregarán en HU09 (Reacciones). Mientras esa HU no esté implementada, el response de creación los omite en lugar de devolver valores hardcodeados.*
- **`400 Bad Request`:** El contenido está vacío y no hay media adjunta.

---

## HU08: Generación de Feed por Recorrido de Grafo
- **Historia de Usuario:** HU08 / Tarea 09
- **Responsables:** Backend 2 (Cypher Feed) & Frontend (Feed Timeline)
- **Método y Ruta:** `GET /api/feed?page=1&pageSize=20`
- **Autenticación:** Obligatoria (JWT)

### Respuestas del Servidor (Responses)
- **`200 OK`:**
```json
{
  "data": [
    {
      "id": "pst_98765432",
      "content": "La configuración de Docker Compose quedó lista y funcionando.",
      "mediaUrl": "http://localhost:9000/social-media-assets/posts/img_kyoto.jpg",
      "createdAt": "2026-09-24T18:50:00Z",
      "author": {
        "id": "usr_99887766",
        "username": "roberto",
        "fullName": "Roberto Martínez",
        "avatarUrl": "http://localhost:9000/social-media-assets/avatars/roberto.png"
      },
      "reactionCounts": {
        "LIKE": 4
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
- **`401 Unauthorized`:** Token ausente o expirado.

---

## HU09: Reacciones a Publicaciones
- **Historia de Usuario:** HU09 / Tarea 10
- **Responsables:** Backend 2 (Cypher Reacciones) & Frontend (Reaction Buttons)
- **Método y Ruta:** `POST /api/posts/{postId}/react`
- **Autenticación:** Obligatoria (JWT)

### Esquema de la Petición (Request Body)
```json
{
  "type": "LIKE"                       // String, requerido. Valores permitidos: ["LIKE", "LOVE", "CELEBRATE"]
}
```

### Respuestas del Servidor (Responses)
- **`200 OK`:**
```json
{
  "postId": "pst_98765432",
  "reactionType": "LIKE",
  "status": "ADDED",                  // String: "ADDED", "REMOVED" (toggle), "UPDATED"
  "totalReactions": 5
}
```
- **`400 Bad Request`:** Tipo de reacción no soportado.
- **`404 Not Found`:** El `postId` no existe en Neo4j.

---

## HU10: Chat Bidireccional en Tiempo Real vía WebSocket
- **Historia de Usuario:** HU10 / Tareas 11, 12 y 13
- **Responsables:** Backend 2 (WebSocket Server) & Frontend (Chat Client)
- **URL de Conexión:** `ws://localhost:8080/ws/chat?token=<TOKEN_JWT>`

### 10.1. Handshake y Control de Sesión
- **Éxito:** Código `101 Switching Protocols`. Sesión registrada en la memoria reactiva del backend.
- **Fallo:** Cierre inmediato con código de cierre WebSocket `4401 Unauthorized`.

### 10.2. Mensajes Cliente -> Servidor (Upstream)
- **Acción Enviar Mensaje (`SEND_MESSAGE`):**
```json
{
  "action": "SEND_MESSAGE",
  "recipientId": "usr_99887766",
  "content": "¿Pudiste revisar la consulta Cypher de recomendaciones?"
}
```
- **Acción Escribiendo (`TYPING`):**
```json
{
  "action": "TYPING",
  "recipientId": "usr_99887766",
  "isTyping": true
}
```

### 10.3. Mensajes Servidor -> Cliente (Downstream)
- **Evento Nuevo Mensaje (`NEW_MESSAGE`):**
```json
{
  "event": "NEW_MESSAGE",
  "id": "msg_aabbcc11",
  "senderId": "usr_7f8a9b1c",
  "senderUsername": "juanperez",
  "recipientId": "usr_99887766",
  "content": "¿Pudiste revisar la consulta Cypher de recomendaciones?",
  "timestamp": "2026-09-24T19:05:32Z"
}
```
- **Evento Error (`ERROR`):**
```json
{
  "event": "ERROR",
  "code": "RECIPIENT_NOT_FOUND",
  "message": "El usuario destinatario no existe."
}
```

### 10.4. Endpoint REST de Historial de Chat
- **Método y Ruta:** `GET /api/chat/{recipientId}/history?page=1&pageSize=50`
- **Autenticación:** Obligatoria (JWT)
- **`200 OK`:**
```json
{
  "data": [
    {
      "id": "msg_aabbcc11",
      "senderId": "usr_7f8a9b1c",
      "recipientId": "usr_99887766",
      "content": "¿Pudiste revisar la consulta Cypher de recomendaciones?",
      "createdAt": "2026-09-24T19:05:32Z"
    }
  ],
  "meta": {
    "page": 1,
    "pageSize": 50,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  }
}
```

---

## HU11: Notificaciones Web Push Desacopladas (VAPID)
- **Historia de Usuario:** HU11 / Tareas 14 y 15
- **Responsables:** Backend 1 (Web Push VAPID) & Frontend (Service Worker)

### Sub-interfaz 11.1: Obtener Clave Pública VAPID
- **Método y Ruta:** `GET /api/notifications/vapid-public-key`
- **Autenticación:** Pública
- **`200 OK`:**
```json
{
  "publicKey": "BEl62iUYgUivxIkv69yViEuiBIa-Ib9-SkvH9sYh8Z..."
}
```

### Sub-interfaz 11.2: Registrar Suscripción del Navegador
- **Método y Ruta:** `POST /api/notifications/subscribe`
- **Autenticación:** Obligatoria (JWT)
- **Request Body:**
```json
{
  "endpoint": "https://fcm.googleapis.com/fcm/send/dK98s...",
  "keys": {
    "p256dh": "BNcRdreALRFXTkOOUHK1BCK2M...",
    "auth": "tBHItJI5svbpez7KI4CCXg=="
  }
}
```
- **`200 OK`:** `{ "status": "SUBSCRIBED" }`

### Sub-interfaz 11.3: Payload Cifrado para el Service Worker
Cuando un usuario publica, el Service Worker recibe el siguiente JSON en su evento `push`:
```json
{
  "title": "Nueva publicación de Roberto Martínez",
  "body": "La configuración de Docker Compose quedó lista y funcionando.",
  "icon": "/logo192.png",
  "badge": "/badge.png",
  "data": {
    "url": "/posts/pst_98765432",
    "postId": "pst_98765432",
    "authorId": "usr_99887766"
  }
}
```
