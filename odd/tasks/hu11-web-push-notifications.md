# Feature: HU11 — Notificaciones Web Push Desacopladas (VAPID)

## Objetivo
Implementar el estándar W3C Push API / RFC 8030 firmado con VAPID para que los usuarios de Wyrdly reciban alertas nativas del navegador incluso con la pestaña cerrada, ante eventos sociales del grafo Neo4j: nueva publicación de un seguido, reacciones (Like/Love/Celebrate) a un post propio, nuevo mensaje de chat cuando el destinatario está offline y nuevo seguidor.

## Problema
La HU11 está documentada en el PRD y en el contrato de API (`docs/api-contracts/API_CONTRACT.md`), los campos `pushEndpoint/pushP256dh/pushAuth` ya existen en el nodo `:Usuario` de Neo4j y los tipos `SocialNotification` con `NotificationType = POST_LIKE | POST_BOOST | GRAPH_FOLLOW | CHAT_MESSAGE` ya están definidos en el frontend. Sin embargo, no existe:
- Keypair VAPID ni su endpoint público.
- `POST /api/notifications/subscribe` para persistir la suscripción del navegador.
- `Service Worker` real con handlers `push` y `notificationclick`.
- Servicio asíncrono de despacho cifrado con auto-cleanup de suscripciones caducadas (410 Gone).
- Hooks en `PostService`, `ReactionService`, `FollowService` y `ChatService` que disparen los pushes correspondientes.

El criterio de aceptación del PRD es explícito: "no se considera válido simular notificaciones mediante un `alert()` o `toast` interno de React mientras la app está abierta", por lo que un Service Worker real con `showNotification` es innegociable.

## Por qué
Las notificaciones push convierten la red social distribuida en una plataforma reactiva: el usuario se entera de interacciones sin necesidad de mantener la pestaña abierta, lo que aumenta retención y engagement. Sin push, Wyrdly compite en desventaja contra cualquier red social moderna. La arquitectura debe ser **desacoplada** (dispatcher asíncrono, no bloquea el hilo del request HTTP) para no degradar la latencia del feed ni del chat, y **resiliente** (suscripciones caducadas se limpian automáticamente) para no acumular credenciales inútiles en Neo4j.

## Alcance Autorizado

### Capa 1 — Infraestructura push (backend, reutilizable)
1. **VAPID keypair**: generar par de claves ECDSA P-256 (`prime256v1`), persistir la privada en volumen/secrets, exponer la pública por config. Script de bootstrap idempotente.
2. **Endpoints REST**:
   - `GET /api/notifications/vapid-public-key` (público, retorna `{publicKey}`).
   - `POST /api/notifications/subscribe` (JWT, persiste `endpoint/p256dh/auth` en `:Usuario`).
   - `DELETE /api/notifications/subscribe` (revoca la suscripción actual).
3. **Servicio `PushDispatcher`**: componente CDI asíncrono que cifra payload con `p256dh`+`auth`, firma con VAPID, POST al `endpoint`. Maneja 404/410 → auto-cleanup. Backoff exponencial para 5xx.

### Capa 2 — Integración con eventos existentes (backend)
4. **Hook `PostService.create()`** → push `NEW_POST_FROM_FOLLOWED` a cada `:Usuario` con `[:SIGUE]->(:Post.autor)` que tenga suscripción activa. Payload con snippet de 140 chars y `data.url = /posts/{id}`.
5. **Hook `ReactionService.react()`** → push `POST_LIKE` o `POST_BOOST` al autor del post, salvo auto-reacción.
6. **Hook `FollowService.follow()`** → push `GRAPH_FOLLOW` al usuario seguido. Idempotente.
7. **Hook `ChatService.onMessage()`** → push `CHAT_MESSAGE` al destinatario **solo si no tiene sesión WS abierta** (criterio PRD: cero polling/alert interno).

### Capa 3 — Frontend: Service Worker, suscripción y UX
8. **`/public/sw.js` real** con handlers `push` (parsea JSON + `self.registration.showNotification`) y `notificationclick` (`event.notification.data.url` en focus o `clients.openWindow`).
9. **Hook `usePushSubscription`** que orquesta: `Notification.requestPermission()` → `GET /vapid-public-key` → `pushManager.subscribe({userVisibleOnly: true, applicationServerKey})` → `POST /subscribe`. Maneja errores de permiso denegado, red y 400.
10. **`PushPermissionBanner` funcional** (reemplaza el esqueleto actual) con tres estados: `default` (CTA), `granted` (oculto o "activo"), `denied` (instructivo sin reintento).
11. **E2E BDD** con Cucumber + Playwright: usuario A publica → usuario B (otra pestaña/sesión) recibe push → click → abre `/posts/{id}`.

### Capa 4 — Hardening (transversal)
12. **Telemetría y rate-limiting**: métrica Micrometer `wyrdly.push.dispatch{type,result}`, cap 1 push/seg/recipient (anti-tormenta cuando un autor publica con muchos seguidores).
13. **Documentación operativa** `docs/operations/WEB_PUSH.md`: rotación de claves, prueba con `web-push-cli`/`curl`, troubleshooting 410, fallback cuando el push gateway del navegador no responde.

## Convenciones
- Conventional Commits sin menciones a IA, en español o inglés consistente con el módulo.
- Branch: `feature/hu11-web-push-notifications` siguiendo el `Git Flow` del repo.
- Backend: Spotless + JUnit verdes. Frontend: ESLint + Prettier + Vitest verdes. E2E: cucumber-js dry-run + al menos 1 escenario green.
- Mensajes de commit por task: `feat(push): ...`, `test(push): ...`, `docs(ops): ...`.

## Tareas

- [ ] **TASK-01**: BE-PUSH-1.1 — Generar y persistir el keypair VAPID. Script de bootstrap idempotente, `application.properties` con `wyrdly.push.vapid.*`, volumen Docker para la clave privada. (Commit: `feat(push): add VAPID keypair bootstrap and config`)
- [ ] **TASK-02**: BE-PUSH-1.2 — Endpoints REST `GET /vapid-public-key`, `POST /subscribe`, `DELETE /subscribe`. Validación 400/401, persistencia en `:Usuario`. (Commit: `feat(push): add subscription REST endpoints`)
- [ ] **TASK-03**: BE-PUSH-1.3 — Servicio `PushDispatcher` asíncrono con cifrado, firma VAPID y auto-cleanup de 410 Gone. Test con MockWebServer. (Commit: `feat(push): add async push dispatcher with 410 cleanup`)
- [ ] **TASK-04**: BE-PUSH-2.1 — Hook en `PostService.create()` que dispara `NEW_POST_FROM_FOLLOWED` a cada seguidor con suscripción activa. (Commit: `feat(push): emit NEW_POST_FROM_FOLLOWED on post create`)
- [ ] **TASK-05**: BE-PUSH-2.2 — Hook en `ReactionService.react()` que dispara `POST_LIKE`/`POST_BOOST` al autor del post, excluyendo auto-reacciones. (Commit: `feat(push): emit POST_LIKE and POST_BOOST on reaction`)
- [ ] **TASK-06**: BE-PUSH-2.3 — Hook en `FollowService.follow()` que dispara `GRAPH_FOLLOW` al usuario seguido, idempotente. (Commit: `feat(push): emit GRAPH_FOLLOW on follow`)
- [x] **TASK-07**: BE-PUSH-2.4 — Hook en `ChatService.onMessage()` / `SendMessageUseCaseImpl` que dispara `CHAT_MESSAGE` solo si el destinatario no tiene sesión WS abierta. (Commit: `feat(push): emit CHAT_MESSAGE push notification when recipient offline`)
- [ ] **TASK-08**: FE-PUSH-3.1 — Service Worker real `/public/sw.js` con handlers `push` y `notificationclick`. Registro en `main.tsx`. (Commit: `feat(push): add service worker with push and notificationclick handlers`)
- [ ] **TASK-09**: FE-PUSH-3.2 — Hook `usePushSubscription` con flujo completo de permiso → subscribe → `POST /subscribe`. Manejo de errores. (Commit: `feat(push): add usePushSubscription hook with error handling`)
- [ ] **TASK-10**: FE-PUSH-3.3 — `PushPermissionBanner` funcional con estados `default/granted/denied`. Suite Vitest cubre los tres estados. (Commit: `feat(push): make PushPermissionBanner stateful`)
- [ ] **TASK-11**: FE-PUSH-3.4 — E2E BDD con Cucumber + Playwright: publicación de A → push recibido por B → click abre `/posts/{id}`. (Commit: `test(push): add cucumber bdd scenario for web push delivery`)
- [ ] **TASK-12**: OPS-PUSH-4.1 — Telemetría Micrometer `wyrdly.push.dispatch{type,result}` + rate-limit 1 push/seg/recipient. (Commit: `feat(push): add push metrics and rate limiting`)
- [ ] **TASK-13**: OPS-PUSH-4.2 — `docs/operations/WEB_PUSH.md` con rotación de claves, troubleshooting 410, prueba con `web-push-cli`. (Commit: `docs(ops): add web push operations runbook`)

## Evidencia de Verificación (a completar al cerrar la feature)
- Backend: suite JUnit verde, Spotless sin issues, métrica Prometheus expuesta.
- Frontend: Vitest verde, ESLint 0 errores, Prettier formateado, Vite build OK.
- E2E: `pnpm test:e2e` con el escenario `push-delivery.feature` green.
- Manual: checklist de QA con DevTools mostrando el push nativo y el click abriendo la URL.
- PR abierto contra `develop` con el desglose de commits work-unit.

## Riesgos Identificados
- **Push gateways del navegador (FCM/Mozilla) en entorno local**: el dev local probablemente no recibe push real. Mitigación: tests con MockWebServer + e2e en CI con navegador headless.
- **Rotación de claves VAPID**: si se rotan, todas las suscripciones existentes quedan inútiles. Documentar el procedimiento en `docs/operations/WEB_PUSH.md` y considerar un endpoint de re-suscripción.
- **Tormenta de pushes**: un autor con 10k seguidores publica 1 post → 10k pushes. Mitigación: rate-limit por recipient + cap global configurable.
- **HTTPS obligatorio**: el navegador solo concede `pushManager.subscribe` en contextos seguros. Verificar que el dev usa HTTPS o `localhost` (excepto del push).
