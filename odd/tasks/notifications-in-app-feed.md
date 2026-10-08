# Feature: Notifications in-app feed (popover con datos reales)

- **ID del Feature:** `notifications-in-app-feed`
- **Estado:** En Progreso
- **Ruta de Tareas:** `odd/tasks/notifications-in-app-feed.md`
- **Rama base:** `develop`
- **Rama de trabajo:** `feature/notifications-in-app-feed`
- **TDD:** Habilitado (RED → GREEN → refactor) en cada tarea.

## 1. Contexto y Objetivos

Los PRs #85/#86/#87 cablearon Web Push (OS notifications) end-to-end. Pero la campanita de la navbar muestra `INITIAL_NOTIFICATIONS` hardcoded en `MainLayout.tsx` (Alice Chen, Jonas Weber, datos inventados) y el popover no se ve por falta de `z-index` en el CSS. El usuario quiere:

1. Que las campanita muestre **notificaciones reales** generadas por los listeners de follow y reaction (que ya disparan PushEvent al Push Service).
2. Que el popover abra bien visualmente.
3. Un E2E que verifique el flow completo: trigger backend → notificación visible en la UI.

## 2. Alcance

- **Incluido:**
  - Persistir notificaciones en Neo4j (`:Notificacion` node) cuando los listeners disparan, además del dispatch al Push Service que ya hacen.
  - Endpoint `GET /api/notifications` que devuelve la lista paginada del usuario autenticado.
  - Endpoints `POST /api/notifications/{id}/read` y `POST /api/notifications/mark-all-read`.
  - Hook `useNotifications` en el frontend que hace fetch + mark-read.
  - Reemplazar `INITIAL_NOTIFICATIONS` en `MainLayout` por datos del hook.
  - Fix `z-index` en `.popoverDropdown` del `AppNavbar.module.css`.
  - Feature Cucumber/Playwright E2E: follow + reaction disparan notificación visible en la campanita.
- **Excluido:**
  - WebSocket para notificaciones en tiempo real (pull por polling es suficiente para v1; queda como follow-up).
  - Paginación cursor-based (usamos page/pageSize simples).
  - Actor info enrichment via batch lookup (cada notification trae actor embebido via JOIN en Cypher).

## 3. Diseño de alto nivel

### 3.1 Backend

```
Listener (Follow / Reaction)
       │
       │  Persiste (:Notificacion)
       ▼
Neo4j:es (:Notificacion { ... })
       │
       │  + dispatch (Push Service, ya existía)
       ▼
PushDispatcherPort.dispatch(PushEvent)

GET /api/notifications ──► GetNotificationsForUserUseCase ──► Neo4jNotificationRepositoryAdapter
POST /api/notifications/{id}/read ──► MarkNotificationReadUseCase ──► repo.markRead(id, userId)
POST /api/notifications/mark-all-read ──► MarkAllNotificationsReadUseCase ──► repo.markAllRead(userId)
```

- `:Notificacion` node con propiedades: `id`, `recipientUserId`, `type`, `actorId`, `title`, `body`, `deepLink`, `targetResourceId`, `isRead`, `createdAt`.
- Migration `V101__add_notification_indexes.cypher` con constraint sobre `id` e index compuesto `(recipientUserId, createdAt)` para listar ordenado.
- Lookup del actor (`:Usuario`) se hace vía JOIN en el adapter al listar; si el actor no existe, devuelve un placeholder `{id, username, fullName: "Someone", avatarUrl: null, instanceUrl: "", isVerified: false}`.

### 3.2 Frontend

```
MainLayout → useNotifications() ──► GET /api/notifications ──► state: notifications, unreadCount
                                       │
                                  └─► markRead(id) ──► POST /api/notifications/{id}/read
                                  └─► markAllRead() ──► POST /api/notifications/mark-all-read
                                       │
                                       ▼
                              <NotificationPopover ...> (recibe datos reales)
```

- `useNotifications({ enabled })` retorna `{ notifications, unreadCount, isLoading, error, markRead, markAllRead, refetch }`.
- Poll cada 30s para refrescar (sin WebSocket en esta iteración).
- z-index de `.popoverDropdown` = `50` (Tailwind z-50 equivalente).

### 3.3 E2E

- Feature file Gherkin: `e2e/features/notifications/in-app-feed.feature` con 2 escenarios: follow y reaction.
- Steps en `e2e/steps/notifications/`.
- Usa API real del backend + render del popover.

## 4. Plan de Tareas (Checklist)

### Backend

- [ ] **TASK-01 (domain + adapter + migration):**
  - `notifications/domain/model/Notification.java` (record).
  - `notifications/domain/repository/NotificationRepository.java` (port).
  - `notifications/infrastructure/persistence/Neo4jNotificationRepositoryAdapter.java` con Cypher.
  - `resources/neo4j/migrations/V101__add_notification_indexes.cypher`.
  - Tests: `Neo4jNotificationRepositoryIT` (integration test con Neo4j embebido o testcontainers).
- [ ] **TASK-02 (use cases):**
  - `GetNotificationsForUserUseCase` + impl + test.
  - `MarkNotificationReadUseCase` + impl + test.
  - `MarkAllNotificationsReadUseCase` + impl + test.
  - DTOs: `NotificationDto`, `NotificationListResponseDto`.
- [ ] **TASK-03 (REST):**
  - Extender `NotificationResource` con `GET`, `POST /{id}/read`, `POST /mark-all-read`.
  - Test: `NotificationResourceTest`.
- [ ] **TASK-04 (wire listeners):**
  - `UserFollowPushEventListener` persiste + dispatch (extender con `NotificationRepository`).
  - `PostReactionPushEventListener` persiste + dispatch.
  - Actualizar tests de los listeners.

### Frontend

- [ ] **TASK-05 (useNotifications hook):**
  - `features/notifications/hooks/useNotifications.ts` + `.test.ts`.
  - `features/notifications/types/index.ts`: agregar `NotificationDto` y `NotificationListResponseDto`.
- [ ] **TASK-06 (wiring + z-index):**
  - Reemplazar `INITIAL_NOTIFICATIONS` en `MainLayout` por `useNotifications()`.
  - `AppNavbar.module.css`: agregar `z-index: 50` a `.popoverDropdown`.

### E2E

- [ ] **TASK-07 (Cucumber/Playwright):**
  - `e2e/features/notifications/in-app-feed.feature`: 2 escenarios (follow, reaction).
  - `e2e/steps/notifications/in-app-feed.steps.ts`.
  - `e2e/support/` helpers si necesario.

## 5. Convenciones y Restricciones

- Mensajes de commit: Conventional Commits.
- Rama desde `develop`.
- Backend: arquitectura hexagonal. Adapter en `infrastructure/persistence/`, port en `domain/repository/`.
- Frontend: hook en `features/notifications/hooks/`. CSS fix sin agregar clases nuevas (solo `z-index` en `.popoverDropdown`).
- E2E: feature Gherkin+step definitions en el formato del repo existente.

## 6. Riesgos conocidos

- **Actor info enrichment** puede ser N+1 si hay muchas notifications; lo mitigamos con JOIN en Cypher.
- **Polling cada 30s** puede ser agresivo en escala, OK para v1.
- **El frontend `SocialNotification` tiene `POST_BOOST`** que no usamos en backend (los tipos son POST_LIKE/POST_LOVE/POST_CELEBRATE). Mantenemos el union del frontend tal cual, simplemente nunca emitiremos ese tipo.