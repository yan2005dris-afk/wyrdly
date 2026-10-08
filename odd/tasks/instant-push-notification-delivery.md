# Instant Push Notification Delivery Fixes

## Objective
Harden Web Push notification delivery, optimistic TanStack Query cache management, and subscription lifecycle in PR #103 against race conditions, duplicate dispatches, payload mismatches, and involuntary resubscriptions.

## Scope & Constraints
- Keep SW-to-React communication instant and idempotent.
- Prevent duplicate notification increments when both `BroadcastChannel` and `serviceWorker.postMessage` fire.
- Ensure synthetic optimistic notifications do not break `actor` UI or crash `markRead` with 404s.
- Respect explicit user unsubscribe preferences so `useWebPush` does not re-subscribe against user intent.
- Ensure all Vitest tests pass without regressions.

## Tasks
- [x] `TASK-1`: Implement event-level deduplication for push messages across `BroadcastChannel` and `serviceWorker.postMessage` (`public/sw.js` and `useNotifications.ts`).
- [x] `TASK-2`: Align synthetic notification mapping with backend event contracts (`followerId`, actor fallbacks) and guard `markRead` against synthetic IDs in `useNotifications.ts`.
- [x] `TASK-3`: Respect user opt-out on explicit unsubscribe in `useWebPush.ts` via persistent flag.
- [x] `TASK-4`: Add test coverage for deduplication, synthetic markRead handling, and unsubscribe opt-out persistence.

## Verification Evidence
- Full Vitest suite: 54 suites, 320 tests passing cleanly.
- ESLint: 0 errors, 1 warning (pre-existing in sw.js).
- TypeScript & Vite build (`tsc -b && vite build`): passing without errors.
