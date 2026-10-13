## Estado de Réplica:  ACEPTADO CON OBSERVACIONES

Agradecemos la revisión del equipo auditor. Tras analizar el módulo de notificaciones en el cliente (`useNotifications.ts`, `useNotificationStream.ts`, `axios.ts`), los endpoints del backend (`NotificationResource.java`) y los requisitos del enunciado de la actividad, determinamos lo siguiente:

## 1. Reconocimiento del problema

Se acepta la existencia de respuestas `401 Unauthorized` recurrentes en un escenario concreto:

- El canal de notificaciones en tiempo real (`GET /api/notifications/stream`, Server-Sent Events) se abre en `wyrdly-frontend/src/features/notifications/hooks/useNotificationStream.ts` usando directamente la función `fetch` del navegador (línea 60), en lugar del cliente Axios que usa el resto de la aplicación. Esto es relevante porque la renovación automática del token está programada únicamente en el cliente Axios (`api/axios.ts`): cuando una petición hecha con Axios recibe un `401`, se solicita un token nuevo y se repite la petición. Como el stream no usa Axios, **no renueva el token cuando este vence**.
- El access token JWT tiene una vigencia de **15 minutos** (`expiresIn: 900`). Si la conexión SSE se interrumpe (corte de red, reinicio del backend, cierre por un proxy intermedio) después de que el token expira, el hook reintenta la conexión reutilizando el token vencido (`getAccessToken()`, línea 49). El backend lo rechaza correctamente con `401`, y el hook vuelve a reintentar con *backoff* exponencial (líneas 122–123, hasta 30 s) **sin renovar el token**, generando una secuencia de errores `401` en la consola hasta que otra petición realizada con Axios dispara el refresco.
- La falla raíz es la ausencia de renovación de credenciales en el canal SSE, no un rechazo indebido del backend.

## 2. Observaciones técnicas y contextualización

**El módulo sí está integrado con la capa de seguridad JWT:**
Se verificó en el entorno levantado con Docker Compose, a través del proxy del frontend, con un usuario recién registrado:

| Petición | Token | Resultado |
|---|---|---|
| `GET /api/notifications?page=0&pageSize=20` | JWT válido | `200 OK` — `{"notifications":[],"unreadCount":0,...}` |
| `GET /api/notifications/stream` | JWT válido | `200 OK` — stream SSE abierto |
| `GET /api/notifications?page=0&pageSize=20` | JWT inválido / vencido | `401 Unauthorized` |
| `GET /api/notifications/stream` | JWT inválido / vencido | `401 Unauthorized` |

- La consulta de la lista (`useNotifications.ts`, línea 56) usa `apiClient`, cuyo interceptor de petición adjunta `Authorization: Bearer <token>` en cada llamada, y cuyo interceptor de respuesta, ante un `401`, renueva el token vía `/api/auth/refresh` y reintenta la petición de forma transparente.
- El stream SSE también adjunta el header `Authorization` explícitamente.
- Los endpoints del backend están protegidos con `@Authenticated` y responden `401` solo ante tokens ausentes, inválidos o vencidos, que es el comportamiento esperado de la capa de seguridad.

Por lo tanto, la afirmación de que el endpoint *"devuelve constantemente un error 401"* para un usuario autenticado y de que el módulo *"no está integrado correctamente con la capa de seguridad JWT"* no se sostiene: con una sesión vigente las llamadas devuelven `200 OK`.

**No existe polling sobre el endpoint de notificaciones:**
El cliente no utiliza `setInterval` ni `refetchInterval`. La lista se obtiene al montar el layout, al recuperar el foco de la pestaña (`refetchOnWindowFocus`) y cuando llega un evento por SSE o un mensaje del Service Worker (Web Push). Las actualizaciones en tiempo real llegan por el stream SSE, no por consultas periódicas.

**Cumplimiento del enunciado:**

- **Sección 5 (*Notificaciones Web Push*):** el requisito de notificar a los seguidores cuando un usuario publica se cumple mediante Web Push real (claves VAPID y Service Worker). Este canal es independiente del stream SSE y **no se ve afectado** por el defecto descrito.
- **Sección 4 (*Mensajería en tiempo real*):** la prohibición de polling aplica al chat, que se implementa con WebSocket. Aun así, el módulo de notificaciones tampoco recurre a polling.
- **Sección 11 (*Restricciones*):** el módulo no constituye un componente "no integrado": sus endpoints están autenticados, conectados a Neo4j y consumidos por el frontend con resultados correctos.

El stream SSE in-app es una mejora adicional sobre los requisitos mínimos, y el defecto afecta únicamente a su reconexión tras la expiración del token.

**Reclasificación de severidad:**
Proponemos reclasificar el hallazgo de **Alta** a **Media / Manejo de sesión en canal en tiempo real**. No hay pérdida de datos ni brecha de seguridad (el backend rechaza correctamente tokens vencidos), la lista de notificaciones se recupera mediante el refresco de Axios, y las notificaciones Web Push exigidas por el enunciado siguen entregándose. El impacto se limita a errores `401` repetidos en consola y a una demora en la recepción in-app mientras el token no se renueva.

## 3. Acciones correctivas planificadas

- **Renovación del token en el canal SSE (Frontend):** ante una respuesta `401` del stream, `useNotificationStream` invocará el mismo flujo de refresco que `apiClient` (reutilizando su cola de peticiones para evitar refrescos concurrentes) y reconectará con el token renovado. Si el refresco falla, se emitirá `wyrdly:session-expired` y se detendrán los reintentos.
- **Detener reintentos ante errores no recuperables:** los códigos `401`/`403` no se tratarán como fallos de red transitorios; el *backoff* exponencial solo aplicará a errores de conectividad y `5xx`.
- **Renovación proactiva:** reconectar el stream antes de la expiración del token (por ejemplo, al renovarse el access token), evitando que una reconexión use credenciales vencidas.
- **Prueba de regresión:** agregar un test del hook que simule un `401` en el stream y verifique que se renueva el token y se reconecta una sola vez.
