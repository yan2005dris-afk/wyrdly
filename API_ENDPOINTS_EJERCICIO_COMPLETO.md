# API Endpoints - Yaga Social
**Documentación Completa con Ejercicio de Prueba Paso a Paso**

---

## Base URL
```
http://localhost:8081
```

⚠️ **Nota:** Puerto 8081 es el usado por Quarkus en dev mode.

---

## 1. AUTENTICACIÓN

### 1.1 Registrar Usuario
**Método:** `POST`  
**URL:** `/api/auth/register`  
**Content-Type:** `application/json`

**Request:**
```json
{
  "username": "juan_perez",
  "email": "juan@example.com",
  "password": "MiPassword123",
  "fullName": "Juan Pérez García",
  "bio": "Desarrollador apasionado por la tecnología",
  "avatarUrl": "https://example.com/avatar-juan.jpg"
}
```

**Response (201 CREATED):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 3600,
  "user": {
    "id": "uuid-user-1",
    "username": "juan_perez",
    "email": "juan@example.com",
    "fullName": "Juan Pérez García",
    "bio": "Desarrollador apasionado por la tecnología",
    "avatarUrl": "https://example.com/avatar-juan.jpg",
    "createdAt": "2026-09-29T10:30:00Z"
  }
}
```

**Validaciones:**
- Username: 3-30 caracteres, solo alfanuméricos y guiones bajos
- Email: Debe ser válido
- Password: Mínimo 6 caracteres
- Full Name: Máximo 100 caracteres
- Bio: Opcional
- Avatar URL: Opcional, debe empezar con http:// o https://

---

### 1.2 Login
**Método:** `POST`  
**URL:** `/api/auth/login`  
**Content-Type:** `application/json`

**Request:**
```json
{
  "usernameOrEmail": "juan_perez",
  "password": "MiPassword123"
}
```

**Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 3600,
  "user": {
    "id": "uuid-user-1",
    "username": "juan_perez",
    "email": "juan@example.com",
    "fullName": "Juan Pérez García",
    "bio": "Desarrollador apasionado por la tecnología",
    "avatarUrl": "https://example.com/avatar-juan.jpg",
    "createdAt": "2026-09-29T10:30:00Z"
  }
}
```

**Cookie:** El servidor también devuelve `refreshToken` en una cookie HttpOnly segura.

---

### 1.3 Refrescar Token
**Método:** `POST`  
**URL:** `/api/auth/refresh`  
**Content-Type:** `application/json`

**Request (opción 1 - con body):**
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Request (opción 2 - con cookie):**
- El refresh token se envía automáticamente como cookie `refreshToken`

**Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expiresIn": 3600,
  "user": {
    "id": "uuid-user-1",
    "username": "juan_perez",
    "email": "juan@example.com",
    "fullName": "Juan Pérez García",
    "bio": "Desarrollador apasionado por la tecnología",
    "avatarUrl": "https://example.com/avatar-juan.jpg",
    "createdAt": "2026-09-29T10:30:00Z"
  }
}
```

---

### 1.4 Obtener Usuario Actual
**Método:** `GET`  
**URL:** `/api/auth/me`  
**Headers:** 
```
Authorization: Bearer {token}
```
**Autenticación:** ✅ Requerida

**Response (200 OK):**
```json
{
  "id": "uuid-user-1",
  "username": "juan_perez",
  "email": "juan@example.com",
  "fullName": "Juan Pérez García",
  "bio": "Desarrollador apasionado por la tecnología",
  "avatarUrl": "https://example.com/avatar-juan.jpg",
  "createdAt": "2026-09-29T10:30:00Z"
}
```

---

### 1.5 Logout
**Método:** `POST`  
**URL:** `/api/auth/logout`  
**Headers:** 
```
Authorization: Bearer {token}
```

**Response (200 OK):**
```json
{
  "message": "Logged out successfully"
}
```

**Cookie:** El servidor limpia la cookie `refreshToken`.

---

## 2. PERFIL DE USUARIO

### 2.1 Obtener Perfil de Usuario
**Método:** `GET`  
**URL:** `/api/users/{username}`  
**Headers (Opcional):**
```
Authorization: Bearer {token}
```
**Autenticación:** ❌ No requerida, pero si se envía token muestra si ya sigues al usuario

**Response (200 OK):**
```json
{
  "id": "uuid-user-1",
  "username": "juan_perez",
  "fullName": "Juan Pérez García",
  "bio": "Desarrollador apasionado por la tecnología",
  "avatarUrl": "https://example.com/avatar-juan.jpg",
  "followersCount": 150,
  "followingCount": 85,
  "isFollowing": false,
  "createdAt": "2026-09-29T10:30:00Z"
}
```

**Ejemplo:**
```bash
curl -X GET "http://localhost:8081/api/users/juan_perez" \
  -H "Authorization: Bearer {token}"
```

---

### 2.2 Actualizar Perfil Propio
**Método:** `PUT`  
**URL:** `/api/users/profile`  
**Content-Type:** `application/json`  
**Headers:**
```
Authorization: Bearer {token}
```
**Autenticación:** ✅ Requerida

**Request:**
```json
{
  "fullName": "Juan Pérez García Actualizado",
  "bio": "Desarrollador senior en Python y Java",
  "avatarUrl": "https://example.com/nuevo-avatar.jpg"
}
```

**Response (200 OK):**
```json
{
  "id": "uuid-user-1",
  "username": "juan_perez",
  "fullName": "Juan Pérez García Actualizado",
  "bio": "Desarrollador senior en Python y Java",
  "avatarUrl": "https://example.com/nuevo-avatar.jpg",
  "followersCount": 150,
  "followingCount": 85,
  "isFollowing": false,
  "createdAt": "2026-09-29T10:30:00Z"
}
```

**Validaciones:**
- Full Name: Máximo 100 caracteres
- Bio: Máximo 250 caracteres
- Avatar URL: Máximo 2048 caracteres, debe empezar con http:// o https://

---

## 3. SISTEMA DE SEGUIMIENTO (Follow/Unfollow)

### 3.1 Seguir Usuario
**Método:** `POST`  
**URL:** `/api/users/{targetUserId}/follow`  
**Headers:**
```
Authorization: Bearer {token}
```
**Autenticación:** ✅ Requerida

**Request:** Sin body

**Response (200 OK):**
```json
{
  "message": "You are now following uuid-user-2",
  "targetUserId": "uuid-user-2",
  "following": true
}
```

**Ejemplo:**
```bash
curl -X POST "http://localhost:8081/api/users/uuid-user-2/follow" \
  -H "Authorization: Bearer {token}"
```

---

### 3.2 Dejar de Seguir Usuario
**Método:** `DELETE`  
**URL:** `/api/users/{targetUserId}/follow`  
**Headers:**
```
Authorization: Bearer {token}
```
**Autenticación:** ✅ Requerida

**Request:** Sin body

**Response (200 OK):**
```json
{
  "message": "You are no longer following uuid-user-2",
  "targetUserId": "uuid-user-2",
  "following": false
}
```

**Ejemplo:**
```bash
curl -X DELETE "http://localhost:8081/api/users/uuid-user-2/follow" \
  -H "Authorization: Bearer {token}"
```

---

## 4. CHAT

### ⚠️ REQUISITO IMPORTANTE: VALIDACIÓN DE FOLLOW
**Ambos usuarios DEBEN seguirse mutuamente para:**
- ✅ Enviar mensajes
- ✅ Ver historial de chat

Si NO se sigue mutuamente, se obtiene:
```json
{
  "status": 403,
  "error": "Ambos usuarios deben seguirse mutuamente para chatear"
}
```

---

### 4.1 Obtener Historial de Chat (REST)
**Método:** `GET`  
**URL:** `/api/chat/{recipientId}/history`  
**Query Parameters:**
- `page`: Número de página (default: 1)
- `pageSize`: Cantidad de mensajes por página (default: 50, máximo: 100)

**Headers:**
```
Authorization: Bearer {token}
```
**Autenticación:** ✅ Requerida

**Response (200 OK):**
```json
{
  "page": 1,
  "pageSize": 50,
  "totalMessages": 127,
  "totalPages": 3,
  "messages": [
    {
      "id": "msg-uuid-1",
      "senderId": "uuid-user-1",
      "recipientId": "uuid-user-2",
      "content": "Hola, ¿cómo estás?",
      "timestamp": "2026-09-29T14:22:00Z",
      "isRead": true
    },
    {
      "id": "msg-uuid-2",
      "senderId": "uuid-user-2",
      "recipientId": "uuid-user-1",
      "content": "¡Bien, gracias! ¿Y tú?",
      "timestamp": "2026-09-29T14:23:15Z",
      "isRead": true
    }
  ]
}
```

**Ejemplo:**
```bash
# Página 1 con 50 mensajes
curl -X GET "http://localhost:8081/api/chat/uuid-user-2/history?page=1&pageSize=50" \
  -H "Authorization: Bearer {token}"

# Página 2 con 25 mensajes
curl -X GET "http://localhost:8081/api/chat/uuid-user-2/history?page=2&pageSize=25" \
  -H "Authorization: Bearer {token}"
```

---

### 4.2 WebSocket - Mensajería en Tiempo Real

#### 🔄 DIFERENCIA: REST vs WebSocket

| Aspecto | REST | WebSocket |
|---------|------|-----------|
| **Conexión** | Request → Response (puntual) | Conexión persistente bidireccional |
| **Protocolo** | HTTP/HTTPS | WS/WSS (sobre TCP) |
| **Latencia** | Alta (nueva conexión cada solicitud) | Baja (conexión abierta) |
| **Datos** | Cliente solicita, servidor responde | Ambos pueden enviar en cualquier momento |
| **Uso** | Consultas esporádicas (historial) | Chat, notificaciones, datos en vivo |

#### WebSocket Conexión
**URL:** `ws://localhost:8081/ws/chat`  
**Query Parameters:**
- `token`: JWT token (obligatorio, en URL)

**Headers para upgrade:**
```
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Key: [generado por navegador]
Sec-WebSocket-Version: 13
```

**Ejemplo JavaScript:**
```javascript
const token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...";
const socket = new WebSocket(`ws://localhost:8081/ws/chat?token=${encodeURIComponent(token)}`);

socket.onopen = (event) => {
  console.log("Conectado al servidor de chat");
};

socket.onmessage = (event) => {
  const data = JSON.parse(event.data);
  console.log("Mensaje recibido:", data);
};

socket.onerror = (error) => {
  console.error("Error WebSocket:", error);
};

socket.onclose = () => {
  console.log("Desconectado del servidor");
};
```

#### Acciones WebSocket

##### 4.2.1 Enviar Mensaje (SEND_MESSAGE)
**Cliente → Servidor:**
```json
{
  "action": "SEND_MESSAGE",
  "recipientId": "uuid-user-2",
  "content": "Hola, ¿cómo estás?"
}
```

**Servidor → Cliente Remitente (confirmación):**
```json
{
  "action": "MESSAGE_SENT",
  "message": {
    "id": "msg-uuid-1",
    "senderId": "uuid-user-1",
    "recipientId": "uuid-user-2",
    "content": "Hola, ¿cómo estás?",
    "sentAt": "2026-09-29T14:22:00Z"
  }
}
```

**Servidor → Cliente Destinatario (si está online):**
```json
{
  "action": "MESSAGE_RECEIVED",
  "message": {
    "id": "msg-uuid-1",
    "senderId": "uuid-user-1",
    "recipientId": "uuid-user-2",
    "content": "Hola, ¿cómo estás?",
    "sentAt": "2026-09-29T14:22:00Z"
  }
}
```

##### 4.2.2 Indicador de Escritura (TYPING)
**Cliente → Servidor:**
```json
{
  "action": "TYPING",
  "recipientId": "uuid-user-2"
}
```

**Servidor → Cliente Destinatario (broadcast):**
```json
{
  "action": "USER_TYPING",
  "userId": "uuid-user-1"
}
```

##### 4.2.3 Conexión Establecida (Automático)
**Servidor → Cliente (al conectar):**
```json
{
  "action": "CONNECTION_ESTABLISHED",
  "userId": "uuid-user-1",
  "message": "Connected to chat server"
}
```

##### 4.2.4 Errores WebSocket
**Servidor → Cliente (validación falla):**
```json
{
  "action": "ERROR",
  "message": "Ambos usuarios deben seguirse mutuamente para chatear"
}
```

**Ejemplo Completo: Flujo de Chat en Tiempo Real**
```javascript
const socket = new WebSocket(`ws://localhost:8081/ws/chat?token=${token}`);

socket.onmessage = (event) => {
  const msg = JSON.parse(event.data);
  
  switch(msg.action) {
    case "CONNECTION_ESTABLISHED":
      console.log("Conectado como:", msg.userId);
      break;
      
    case "MESSAGE_SENT":
      console.log("Tu mensaje fue enviado:", msg.message.id);
      break;
      
    case "MESSAGE_RECEIVED":
      console.log("Nuevo mensaje de", msg.message.senderId, ":", msg.message.content);
      break;
      
    case "USER_TYPING":
      console.log("Usuario", msg.userId, "está escribiendo...");
      break;
      
    case "ERROR":
      console.error("Error:", msg.message);
      break;
  }
};

// Enviar mensaje
socket.send(JSON.stringify({
  action: "SEND_MESSAGE",
  recipientId: "uuid-user-2",
  content: "Hola desde WebSocket"
}));

// Indicar que estoy escribiendo
socket.send(JSON.stringify({
  action: "TYPING",
  recipientId: "uuid-user-2"
}));
```

---

## EJERCICIO COMPLETO: DE REGISTRO A CHAT EN TIEMPO REAL

### Escenario Completo
Crear dos usuarios, autenticarse, hacerlos seguirse mutuamente, chatear vía REST e integración con WebSocket en tiempo real.

### Variables a Guardar
```bash
# Después de cada paso, guardar:
TOKEN_JUAN=""
USER_ID_JUAN=""
TOKEN_MARIA=""
USER_ID_MARIA=""
```

---

## PASO 1: Registrar Usuario Juan

```bash
curl -X POST "http://localhost:8081/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "juan_test_001",
    "email": "juan.test.001@example.com",
    "password": "Password123!",
    "fullName": "Juan Pérez",
    "bio": "Desarrollador Python",
    "avatarUrl": "https://example.com/avatars/juan.jpg"
  }'
```

**Response:** Copiar y guardar `token` y `user.id` del JSON respuesta:
```
TOKEN_JUAN = (valor del "token")
USER_ID_JUAN = (valor del "id" en objeto user)
```

---

## PASO 2: Registrar Usuario María

```bash
curl -X POST "http://localhost:8081/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "maria_test_001",
    "email": "maria.test.001@example.com",
    "password": "Password456!",
    "fullName": "María García",
    "bio": "Desarrolladora Java",
    "avatarUrl": "https://example.com/avatars/maria.jpg"
  }'
```

**Response:** Copiar y guardar `token` y `user.id`:
```
TOKEN_MARIA = (valor del "token")
USER_ID_MARIA = (valor del "id" en objeto user)
```

---

## PASO 3: Juan Obtiene su Perfil Actual

```bash
curl -X GET "http://localhost:8081/api/auth/me" \
  -H "Authorization: Bearer $TOKEN_JUAN"
```

**Verificar que devuelve el perfil de Juan**

---

## PASO 4: Juan Sigue a María

```bash
curl -X POST "http://localhost:8081/api/users/$USER_ID_MARIA/follow" \
  -H "Authorization: Bearer $TOKEN_JUAN"
```

**Response esperada:**
```json
{
  "message": "Usuario seguido exitosamente.",
  "targetUserId": "USER_ID_MARIA",
  "following": true
}
```

---

## PASO 5: María Sigue a Juan

```bash
curl -X POST "http://localhost:8081/api/users/$USER_ID_JUAN/follow" \
  -H "Authorization: Bearer $TOKEN_MARIA"
```

**Response esperada:**
```json
{
  "message": "Usuario seguido exitosamente.",
  "targetUserId": "USER_ID_JUAN",
  "following": true
}
```

---

## PASO 6: Verificar Perfil de Juan (desde María)

```bash
curl -X GET "http://localhost:8081/api/users/juan_test_001" \
  -H "Authorization: Bearer $TOKEN_MARIA"
```

**Verificar:** `"isFollowing": true` y `"followersCount": 1`

---

## PASO 7: Actualizar Perfil de Juan

```bash
curl -X PUT "http://localhost:8081/api/users/profile" \
  -H "Authorization: Bearer $TOKEN_JUAN" \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "Juan Pérez García",
    "bio": "Desarrollador Senior Python y Java",
    "avatarUrl": "https://example.com/avatars/juan-updated.jpg"
  }'
```

---

## PASO 8: Obtener Historial de Chat (REST) - Página 1

```bash
curl -X GET "http://localhost:8081/api/chat/$USER_ID_MARIA/history?page=1&pageSize=50" \
  -H "Authorization: Bearer $TOKEN_JUAN"
```

**Response:** Historial de mensajes (posiblemente vacío al inicio)

---

## PASO 9: Obtener Historial de Chat - Página 2

```bash
curl -X GET "http://localhost:8081/api/chat/$USER_ID_MARIA/history?page=2&pageSize=25" \
  -H "Authorization: Bearer $TOKEN_JUAN"
```

**Prueba de paginación**

---

## PASO 10: Conectar a WebSocket (CHAT EN TIEMPO REAL)

### Opción A: JavaScript en Navegador

Abrir consola del navegador (F12) y ejecutar:

```javascript
const token = "PEGA_TOKEN_JUAN_AQUI";
const socket = new WebSocket(`ws://localhost:8081/ws/chat?token=${encodeURIComponent(token)}`);

socket.onopen = (event) => {
  console.log("✅ Conectado al servidor de chat");
};

socket.onmessage = (event) => {
  const msg = JSON.parse(event.data);
  console.log("📨 Mensaje recibido:", msg.action);
  console.log(msg);
};

socket.onerror = (error) => {
  console.error("❌ Error WebSocket:", error);
};

socket.onclose = () => {
  console.log("❌ Desconectado del servidor");
};

// GUARDAR en variable global para usar después
window.socketJuan = socket;
```

**Verificar en consola:** Debe ver `✅ Conectado al servidor de chat`

---

## PASO 11: Enviar Mensaje vía WebSocket (Juan → María)

En la consola del navegador (desde Paso 10):

```javascript
window.socketJuan.send(JSON.stringify({
  action: "SEND_MESSAGE",
  recipientId: "PEGA_USER_ID_MARIA_AQUI",
  content: "Hola María, ¿recibes este mensaje?"
}));
```

**Verificar en consola:** Debe recibir respuesta con `action: "MESSAGE_SENT"`

---

## PASO 12: Indicador de Escritura (Typing)

En la consola:

```javascript
window.socketJuan.send(JSON.stringify({
  action: "TYPING",
  recipientId: "PEGA_USER_ID_MARIA_AQUI"
}));
```

---

## PASO 13: Conectar Segunda Sesión (María) - SIMULTANEO

**Abrir otra pestaña o ventana del navegador** e ir a la consola (F12):

```javascript
const token = "PEGA_TOKEN_MARIA_AQUI";
const socket = new WebSocket(`ws://localhost:8081/ws/chat?token=${encodeURIComponent(token)}`);

socket.onopen = () => {
  console.log("✅ María conectada");
};

socket.onmessage = (event) => {
  const msg = JSON.parse(event.data);
  console.log("📨 María recibió:", msg.action);
  console.log(msg);
};

window.socketMaria = socket;
```

---

## PASO 14: María Envía Mensaje a Juan

**En la consola de María:**

```javascript
window.socketMaria.send(JSON.stringify({
  action: "SEND_MESSAGE",
  recipientId: "PEGA_USER_ID_JUAN_AQUI",
  content: "¡Perfecto! Recibí tu mensaje"
}));
```

**Verificar en la consola de Juan:** Debe recibir `action: "MESSAGE_RECEIVED"` con el mensaje de María

---

## PASO 15: Verificar Historial (REST nuevamente)

```bash
curl -X GET "http://localhost:8081/api/chat/$USER_ID_MARIA/history?page=1&pageSize=50" \
  -H "Authorization: Bearer $TOKEN_JUAN"
```

**Verificar:** Los mensajes de WebSocket deben estar guardados en el historial

---

## PASO 16: Dejar de Seguir (Opcional - Probar restricción)

Juan deja de seguir a María:

```bash
curl -X DELETE "http://localhost:8081/api/users/$USER_ID_MARIA/follow" \
  -H "Authorization: Bearer $TOKEN_JUAN"
```

**Intentar enviar mensaje (debe fallar con 403):**

```javascript
// Desde la consola de Juan (después de dejar de seguir):
window.socketJuan.send(JSON.stringify({
  action: "SEND_MESSAGE",
  recipientId: "PEGA_USER_ID_MARIA_AQUI",
  content: "Este mensaje debería fallar"
}));
```

**Verificar en consola:** Debe recibir `action: "ERROR"` con mensaje de validación de follow

---

## PASO 17: Volver a Seguir para Reanudar Chat

Juan vuelve a seguir a María:

```bash
curl -X POST "http://localhost:8081/api/users/$USER_ID_MARIA/follow" \
  -H "Authorization: Bearer $TOKEN_JUAN"
```

**Ahora debería funcionar nuevamente el envío de mensajes**

---

## PASO 18: Logout

```bash
curl -X POST "http://localhost:8081/api/auth/logout" \
  -H "Authorization: Bearer $TOKEN_JUAN"
```

**WebSocket de Juan debe desconectarse automáticamente**

---

---

## HERRAMIENTAS RECOMENDADAS PARA PROBAR

### REST API (curl, Postman)
✅ **curl:** Línea de comandos (incluido en la documentación)
✅ **Postman:** GUI, fácil de usar https://www.postman.com
✅ **Insomnia:** Alternativa a Postman https://insomnia.rest

### WebSocket

#### Opción 1: JavaScript Navegador (Recomendado - Sin instalación)
- Abrir DevTools (F12) en cualquier navegador
- Copiar/pegar código JavaScript en la consola
- Ver mensajes en tiempo real

#### Opción 2: wscat (CLI)
```bash
npm install -g wscat

# Conectarse
wscat -c "ws://localhost:8081/ws/chat?token=TU_TOKEN_AQUI"

# Enviar mensaje (en la sesión wscat)
> {"action":"SEND_MESSAGE","recipientId":"USER_ID","content":"Mensaje de prueba"}
```

#### Opción 3: websocat
```bash
# Instalar (Windows/Linux/Mac)
# https://github.com/vi/websocat

websocat "ws://localhost:8081/ws/chat?token=TU_TOKEN_AQUI"
```

#### Opción 4: Postman
- En la versión desktop de Postman se puede hacer WebSocket
- Request → Web Socket
- URL: `ws://localhost:8081/ws/chat?token=TU_TOKEN`
- Enviar/recibir mensajes en formato JSON

---

## ✅ CHECKLIST: Requisitos Cumplidos

### Mensajería en Tiempo Real
- ✅ **Implementado con WebSocket** (no REST repetitivo)
  - Conexión persistente bidireccional
  - Protocolo: WS (WebSocket Secure)
  - Endpoint: `/ws/chat`

- ✅ **Iniciar Conversación**
  - Ambos usuarios deben seguirse mutuamente
  - Validación en `SendMessageUseCase`
  - Validación en `GetChatHistoryUseCase`

- ✅ **Enviar Mensajes**
  - Action: `SEND_MESSAGE` vía WebSocket
  - Validación de contenido (no vacío, ≤5000 caracteres)
  - Validación de usuarios (no auto-mensajes)
  - Validación de follow bidireccional

- ✅ **Recibir Mensajes en Tiempo Real**
  - Broadcast a usuario online si está conectado
  - Almacenamiento en BD para usuarios offline
  - Action: `MESSAGE_RECEIVED` con mensaje completo

- ✅ **Visualizar Historial**
  - Endpoint REST: `GET /api/chat/{recipientId}/history`
  - Paginación: `page` y `pageSize` (máx 100)
  - Requiere follow bidireccional
  - Ordenado por timestamp descendente

### Diferencia REST vs WebSocket
| Característica | REST | WebSocket |
|---|---|---|
| **Modelo** | Request → Response | Conexión persistente bidireccional ✅ |
| **Latencia** | Alta (nueva conexión por request) | Baja (conexión abierta) ✅ |
| **Servidor envia datos** | Solo en response | Cuando quiera ✅ |
| **Usado para chat** | No (sondeo) | Sí ✅ |

---

## Notas de Implementación

### Autenticación
- Los tokens JWT se devuelven como `token` en la respuesta de registro/login
- Se debe enviar en el header: `Authorization: Bearer {token}`
- El token expira después de 1 hora (3600 segundos)
- Usar `refreshToken` para obtener un nuevo token sin re-autenticarse
- El servidor devuelve también una cookie `refreshToken` (HttpOnly, segura)

### Seguimiento
- Solo usuarios autenticados pueden seguir/dejar de seguir
- La respuesta incluye `isFollowing` para saber el estado actual
- No hay límite de seguimientos
- **IMPORTANTE:** Dos usuarios DEBEN seguirse mutuamente para chatear
- Un usuario NO puede seguirse a sí mismo (HTTP 400)

### Chat REST
- Endpoint: `GET /api/chat/{recipientId}/history`
- Requiere autenticación JWT
- Paginación: `page` (default 1) y `pageSize` (default 50, máximo 100)
- **IMPORTANTE:** Ambos usuarios deben seguirse mutuamente
  - Si no: HTTP 403 FORBIDDEN

### Chat WebSocket
- URL: `ws://localhost:8081/ws/chat?token=TOKEN_AQUI`
- Requiere token en la URL (no en headers)
- Conexión persistente bidireccional
- Servidor validará follow relationship al enviar mensajes
- Máximo contenido: 5000 caracteres
- No permitir auto-mensajes (mismo usuario como remitente y destinatario)

### Mensajes JSON WebSocket
Todos los mensajes deben ser JSON válido con estructura:
```json
{
  "action": "ACTION_NAME",
  "recipientId": "user-id",
  "content": "mensaje"
}
```

---

## ERRORES COMUNES Y SOLUCIONES

### 401 Unauthorized
```json
{
  "status": 401,
  "message": "Unauthorized - Token inválido o expirado"
}
```
**Solución:** 
- Verificar que el token es correcto
- Token expiró → usar `/api/auth/refresh` con refreshToken
- Asegurar formato: `Authorization: Bearer {token}` en headers

### 403 Forbidden (Follow validation)
```json
{
  "status": 403,
  "message": "Ambos usuarios deben seguirse mutuamente para chatear"
}
```
**Solución:**
- PASO 4 y 5 del ejercicio: ambos deben seguirse
- Verificar con GET `/api/users/{username}` que `"isFollowing": true`

### 404 User Not Found
```json
{
  "status": 404,
  "message": "User not found"
}
```
**Solución:**
- Verificar que el userId o username existe
- Usuario no registrado aún

### 400 Bad Request
```json
{
  "status": 400,
  "message": "Validation error - Username already exists"
}
```
**Solución:**
- Username duplicado → cambiar a uno único (ej: juan_test_001, juan_test_002)
- Email duplicado → cambiar email
- Password muy corto (< 6 caracteres)
- Nombre de usuario con caracteres inválidos

### WebSocket Connection Refused
```
Error: WebSocket is closed before the connection is established.
```
**Solución:**
- Verificar que servidor está corriendo en puerto 8081
- Token es inválido o expiró
- URL correcta: `ws://localhost:8081/ws/chat?token=TOKEN`

### WebSocket Error Action
```json
{
  "action": "ERROR",
  "message": "Ambos usuarios deben seguirse mutuamente para chatear"
}
```
**Solución:**
- Ambos usuarios deben seguirse → ejecutar PASOS 4 y 5
- Después de dejar de seguir → volver a seguir (PASO 17)

---

## FLUJO ESPERADO COMPLETO

```
┌─────────────────────────────────────────────────────────┐
│ 1. REGISTRO                                             │
│    POST /api/auth/register → token + userId             │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│ 2. FOLLOW MUTUO                                         │
│    POST /api/users/{id}/follow (ambos usuarios)         │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│ 3. CHAT - OPCIÓN A: REST (Historial)                   │
│    GET /api/chat/{recipientId}/history?page=1          │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│ 4. CHAT - OPCIÓN B: WebSocket (Tiempo Real)             │
│    WS /ws/chat?token=...                                │
│    → MESSAGE_SENT / MESSAGE_RECEIVED                    │
└─────────────────────────────────────────────────────────┘
```

---

## Archivos Modificados

### Backend (Validación Follow en Chat)
- `SendMessageUseCaseImpl.java` - Agrega `validateFollowRelationship()`
- `GetChatHistoryUseCaseImpl.java` - Agrega `validateFollowRelationship()`
- `UsersNotFollowingException.java` - Nueva excepción (HTTP 403)
- `ChatExceptionMappers.java` - Nuevo mapper para la excepción
- `ChatWebSocketEndpoint.java` - Mejorado manejo de excepciones en `handleSendMessage()`

### Documentación
- Este archivo - Documentación completa de requisitos y pruebas

