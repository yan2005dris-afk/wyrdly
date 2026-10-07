# Feature: Activación end-to-end de Web Push (HU11) + cableado a Follow y Reaction

- **ID del Feature:** `web-push-activation`
- **Estado:** En Progreso
- **Ruta de Tareas:** `odd/tasks/web-push-activation.md`
- **Rama base:** `develop`
- **Ramas de trabajo:** `feature/web-push-foundation`, `feature/web-push-follow`, `feature/web-push-reaction`
- **TDD:** Habilitado por defecto para los listeners y el SW (test-first con RED → GREEN → refactor).

## 1. Contexto y Objetivos

La base backend de HU11 ya está mergeada (`PushDispatcherImpl`, `VapidKeyProvider`, `SubscribeToPushUseCase`, `Neo4jPushSubscriptionRepositoryAdapter`, `MessageEncryptor`, `VapidJwtSigner`). Pero el sistema está **desconectado** de los eventos de dominio: ningún observador CDI cablea `UserFollowRelationshipChangedEvent` ni las reacciones con el dispatcher, y en el frontend no existe service worker, ni el flow de suscripción, ni la integración con `PushPermissionBanner`. Resultado: hoy nadie recibe push en el navegador aunque la suscripción exista.

Objetivos:
1. Que cualquier push realmente llegue al navegador del destinatario (service worker + flow de suscripción).
2. Que un follow dispare push al usuario seguido (HU04 → HU11).
3. Que una reacción dispare push al autor del post (HU09 → HU11).
4. Cubrir el comportamiento con tests (listeners + SW handlers + hooks frontend).

## 2. Alcance

- **Incluido:**
  - **PR 0 `feature/web-push-foundation`:** service worker `public/sw.js` (push, `notificationclick`, `pushsubscriptionchange`, install/activate), hook `useWebPush` (registra SW, pide permiso, llama a `/vapid-public-key`, hace `pushManager.subscribe`, POST a `/api/notifications/subscribe`), integración del `PushPermissionBanner` con el hook, tests del hook y del SW. Backend: solo verificación de que `SubscribeToPushUseCase` + `UnsubscribeFromPushUseCase` ya cubren el contrato (sin código nuevo).
  - **PR 1 `feature/web-push-follow`:** puerto `PushDispatcherPort` en `notifications/application/port/`, `PushDispatcherImpl` lo implementa, `UserFollowRelationshipEventListener` observa `UserFollowRelationshipChangedEvent` y dispara `PushEvent` con `type=GRAPH_FOLLOW` solo cuando `followed=true` (self-follow ya filtrado upstream), filtrado defensivo `followerId != targetUserId`, lookup del follower para `actorName`, tests del listener.
  - **PR 2 `feature/web-push-reaction`:** `PostReactionEvent` (post/domain/event), `ReactionService` lo dispara cuando `ReactionStatus != REMOVED`, `PostReactionPushEventListener` lo observa y dispara `PushEvent` con `type=POST_LIKE | POST_LOVE | POST_CELEBRATE` al autor del post, filtrado self-reaction, extensión del union `NotificationType` en `features/notifications/types/index.ts` para `POST_LOVE` y `POST_CELEBRATE`, tests.
- **Excluido:**
  - Chat push notifications (HU10 → HU11): otra iteración.
  - Push para comentarios u otros eventos.
  - Cambios al contrato VAPID o al dispatcher (queda como está; el PR 0 lo deja validado por tests).
  - Presigned URLs / reescritura del upload multimedia.

## 3. Diseño de alto nivel

### 3.1 Backend — puerto y listeners

```
domain event ─fire()─▶ CDI ─observe()─▶ XxxPushEventListener ─build PushEvent─▶ PushDispatcherPort ─▶ PushDispatcherImpl ─▶ Push Service
```

- Se introduce `PushDispatcherPort { void dispatch(PushEvent); }` en `com.wyrdly.notifications.application.port`. `PushDispatcherImpl` lo implementa (mínimo: agregar `implements PushDispatcherPort`; el método ya existe con la misma firma).
- Los listeners viven en `application/listener/` (capa application, hexagonal-clean) y dependen solo de:
  - `Event<DomainEvent>` (entrada CDI)
  - `PushDispatcherPort` (salida al motor)
  - `UserProfileRepository` / `PostRepository` (lookups para enriquecer el payload — opcional pero recomendado).
- Los listeners son `@ApplicationScoped` con `@Observes` sincrónico. La conversión a `PushEvent` y el `dispatcher.dispatch(...)` ocurren en el mismo hilo del evento; `dispatch()` es fire-and-forget porque ya usa un executor interno.
- Reglas de filtrado (defensivas, aunque upstream ya las cumpla):
  - Follow: `!event.followed()` ⇒ no dispatch. `followerId.equals(targetUserId)` ⇒ no dispatch.
  - Reaction: `result.status() == REMOVED` ⇒ no dispatch. `userId.equals(authorId)` ⇒ no dispatch.

### 3.2 Frontend — service worker + hook

```
mount MainLayout
  └─ useWebPush (one-shot on auth)
       ├─ if (!('serviceWorker' in navigator) || !('PushManager' in window)) → return unsupported
       ├─ register('/sw.js') → ready
       ├─ if (Notification.permission === 'default') → setBannerVisible(true)
       └─ on enable:
            ├─ permission = await Notification.requestPermission()
            ├─ if (granted):
            │     vapidKey = await fetch('/api/notifications/vapid-public-key')
            │     sub = await registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: vapidKey })
            │     await api.post('/api/notifications/subscribe', sub.toJSON())
            └─ on denied → banner hidden
       └─ on pushsubscriptionchange → re-subscribe y POST again
```

- `public/sw.js`: handlers para `install`, `activate`, `push` (decodifica JSON, llama `self.registration.showNotification(title, { body, icon, badge, data })`), `notificationclick` (abre `event.notification.data.url` con `clients.openWindow`), `pushsubscriptionchange` (postMessage al cliente para que re-suscriba). Mantener en JS plano (sin bundler) por simplicidad y para no introducir tooling extra.
- Tests del SW: archivo `sw.test.ts` que importa los handlers como funciones puras (refactor mínimo para exponer `handlePush`, `handleNotificationClick`) y los ejecuta contra un `self` mockeado.
- Tests del hook: `useWebPush.test.ts` con msw + jsdom + mocks de `Notification`, `PushManager`.

### 3.3 Tipos de notificación

Extender `NotificationType` del frontend para incluir los nuevos tipos:

```ts
export type NotificationType =
  | "POST_LIKE"
  | "POST_LOVE"
  | "POST_CELEBRATE"
  | "GRAPH_FOLLOW"
  | "CHAT_MESSAGE";
```

`NotificationItem` no necesita cambios (ya renderiza genérico). El badge/ícono se mapea en `NotificationItem.tsx` por tipo — agregar entradas para los nuevos.

## 4. Plan de Tareas (Checklist)

### PR 0 — `feature/web-push-foundation`
- [ ] **TASK-01 (foundation):** Service worker + hook de suscripción + integración con banner.
  - `wyrdly-frontend/public/sw.js` con handlers `push` / `notificationclick` / `pushsubscriptionchange`.
  - `wyrdly-frontend/src/features/notifications/hooks/useWebPush.ts` + `.test.ts` (RED → GREEN → refactor).
  - `PushPermissionBanner` cableado al hook en `MainLayout` (o equivalente).
  - Registro de SW y bootstrap del permiso una vez autenticado.
  - **Verificación:** tests del hook verdes; build OK; suscripción manual con DevTools muestra la entrada en `subscriptions` del backend Neo4j.

### PR 1 — `feature/web-push-follow`
- [ ] **TASK-02 (follow):** Puerto `PushDispatcherPort` + listener para follow.
  - `wyrdly-backend/.../notifications/application/port/PushDispatcherPort.java`.
  - `PushDispatcherImpl implements PushDispatcherPort` (sin cambio funcional).
  - `wyrdly-backend/.../user/application/listener/UserFollowPushEventListener.java` + test.
  - **Verificación:** test del listener verifica que con evento `followed=true` se llama `dispatcher.dispatch(...)` con `type=GRAPH_FOLLOW`, `recipientUserId=targetUserId`; con `followed=false` no se llama; con self-follow no se llama.

### PR 2 — `feature/web-push-reaction`
- [ ] **TASK-03 (reaction):** Evento + listener para reacción + tipos frontend.
  - `wyrdly-backend/.../post/domain/event/PostReactionEvent.java`.
  - `ReactionService.fire(PostReactionEvent)` cuando `status != REMOVED`.
  - `wyrdly-backend/.../post/application/listener/PostReactionPushEventListener.java` + test.
  - Frontend `features/notifications/types/index.ts`: agregar `POST_LOVE` y `POST_CELEBRATE`.
  - `NotificationItem` con icono/badge para los nuevos tipos.
  - **Verificación:** test del listener verifica dispatch con `type` correcto mapeado al `ReactionType`, no dispatch en REMOVED, no dispatch en self-reaction.

## 5. Convenciones y Restricciones

- Mensajes de commit: Conventional Commits.
- Ramas siempre desde `develop` (no desde `fix/jwt-load-private-key` ni de otra feature).
- PRs vía `.github/` si existe template; sino descripción estándar (qué, por qué, cómo verificar).
- Backend: arquitectura hexagonal intacta. Listeners en `application/listener/`, dependen solo de ports y repos.
- Frontend: service worker en `public/` (servido por Nginx en prod, ya configurado).
- Cada PR cierra con al menos un work-unit commit por tarea (pueden ser varios), tests y verificación incluida.

## 6. Riesgos conocidos

- **Permisos de notificación en navegadores headless / CI:** los tests del SW y del hook deben correr en jsdom sin requerir permiso real. `Notification.requestPermission` se mockea.
- **Race conditions en `pushsubscriptionchange`:** puede dispararse mientras el usuario está offline → el handler debe ser idempotente y avisar al cliente vía `postMessage` para re-suscribir cuando vuelva.
- **Cold-start push antes de SW activo:** el SW debe estar registrado y activo al recibir la primera push. El hook usa `registration.ready` (no `register()` solo) para garantizar.
- **Carga útil del push y deep links rotos:** el backend incluye `deepLink` y el frontend SW respeta `data.url`; pero si el usuario no está autenticado, el `openWindow` lo manda a `/login`. Documentado, no es bug.