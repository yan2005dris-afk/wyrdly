# Notification Architecture Refactor: In-App SSE Stream & Background Web Push

## Objective
Decouple in-app real-time notification delivery from background OS push notifications:
- **Server-Sent Events (SSE)** handles immediate, permission-less, lightweight real-time delivery to the active web client (unread counters, query invalidation, in-app toasts).
- **Web Push (VAPID + Service Worker)** is dedicated strictly to native background OS notifications when the application tab is unfocused or closed.

## Scope & Constraints
- **Backend (Quarkus RESTEasy Reactive):**
  - Reactive SSE stream endpoint `GET /api/notifications/stream` producing `text/event-stream`.
  - In-memory reactive broadcaster (`InMemoryNotificationBroadcasterAdapter`) keyed by target user ID.
  - Event listeners (`UserFollowNotificationEventListener`, `PostReactionNotificationEventListener`) dispatch events to the SSE broadcaster upon notification creation.
  - Retain `PushDispatcherPort` and VAPID infrastructure exclusively for background Web Push delivery.
- **Frontend (React + Vite + TanStack Query):**
  - Implement reactive SSE hook (`useNotificationStream`) that connects to `/api/notifications/stream`, handling reconnects and cache invalidation.
  - Decouple frontend `useNotifications` from Service Worker `BroadcastChannel` for in-app UI updates.
  - Keep `sw.js` focused solely on handling `push` events to display native OS notifications (`showNotification`) and sync app badge.
- **Quality & Testing:**
  - Maintain Clean Architecture boundaries (Domain, Application Ports/UseCases, Infrastructure, REST Interfaces).
  - Comprehensive unit and integration test coverage for SSE backend and frontend stream hooks.

## Tasks
- [x] `TASK-1`: Backend - Define `NotificationBroadcasterPort` and in-memory reactive adapter (`Mutiny` / `BroadcastProcessor`).
- [x] `TASK-2`: Backend - Expose SSE streaming endpoint `GET /api/notifications/stream` in `NotificationResource` with auth context.
- [x] `TASK-3`: Backend - Integrate notification listeners to broadcast domain events to connected SSE sessions.
- [x] `TASK-4`: Frontend - Implement `useNotificationStream` SSE client hook with automatic reconnection and query invalidation.
- [x] `TASK-5`: Frontend - Refactor `useNotifications` and `sw.js` to decouple in-app UI state from Service Worker push broadcasts.
- [x] `TASK-6`: Verification - Backend IT / unit test suite and frontend Vitest suite validation.

## Verification Evidence
- Commit: `aa6267b`
- Backend Tests: 34/34 passing (`NotificationResourceTest`, `InMemoryNotificationBroadcasterAdapterTest`, `UserFollowNotificationEventListenerTest`, `PostReactionNotificationEventListenerTest`).
- Spotless: Google Java format clean across all backend classes (`mvn spotless:check` passed).
- Frontend Tests: 56 test files, 329/329 passing (`useNotificationStream.test.ts` 3/3, `useNotifications.test.tsx` 12/12, `useWebPush.test.ts` 12/12, `sw.test.ts` 13/13).
- Frontend Build & Lints: `tsc -b && vite build` succeeded; `eslint` 0 errors, 0 warnings.
