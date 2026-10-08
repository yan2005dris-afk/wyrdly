# Instant Push Notification Delivery Refactor (Option B - Clean Architecture)

## Objective
Radically simplify Web Push handling by eliminating over-engineered secondary fetch paths (IndexedDB token storage, Service Worker background fetches, synthetic fake-ID entities), while retaining critical bugfixes (queryKey memoization, persistent BroadcastChannel, OS badge sync, user opt-out retention).

## Scope & Constraints
- Single source of truth: TanStack Query on the web client owns server state and fetching `/api/notifications`.
- Service Worker's single responsibility: show native notification, update UA badge, and notify active clients via `wyrdly:push-received`.
- Decouple `useAuthStore` from IndexedDB and remove `swAuthToken`.
- Web client invalidates cache on push or visibility change; no synthetic entities with mock IDs or fallback guesswork.
- Preserve explicit user opt-out in `useWebPush` and OS app badge synchronization.

## Tasks
- [x] `TASK-1`: Decouple `useAuthStore` and delete `swAuthToken.ts` / `swAuthToken.test.ts`.
- [x] `TASK-2`: Strip background fetch, IndexedDB access, and `refresh-now` handler from `public/sw.js`, keeping clean push delivery and badge sync.
- [x] `TASK-3`: Simplify `useNotifications.ts` to invalidate TanStack Query on push/focus without synthetic entity injection or fake ID guards.
- [x] `TASK-4`: Align test suites (`sw.test.ts`, `useNotifications.test.tsx`, `useWebPush.test.ts`) and ensure full green suite.

## Verification Evidence
- Vitest suite: 55 files passed, 326/326 tests passed (including `sw.test.ts` 13/13, `useNotifications.test.tsx` 12/12, `useWebPush.test.ts` 7/7).
- ESLint: 0 errors, 0 warnings.
- Build: `tsc -b && vite build` succeeded without error.

