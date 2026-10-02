# Feature: Frontend React Doctor Optimizations & Bugfixes

## Objective
Address critical bugs, performance bottlenecks (unstable context value, memory leaks), and accessibility findings identified by `react-doctor` audit across `wyrdly-frontend`.

## Problem & Rationale
1. **WebSocket Memory Leak**: `useChatWebSocket` was not returning a cleanup function across all branches, risking leaked WebSocket connections on unmount.
2. **Unstable Context Value**: `AuthContext.tsx` was creating a fresh `value` object reference on every render, triggering full tree re-renders across the app.
3. **Object URL Memory Leaks**: `AuthImage.tsx` did not revoke `objectUrl` if unmounted while the image fetch was in-flight.
4. **Resilient Loading State**: `useFeed.ts` and `ChatPage.tsx` were conditionally checking flags inside `finally` blocks, risking un-reset loading state.
5. **A11y and Keyboard Access**: Click events on non-button elements lacked keyboard handlers (`Enter` / `Space`) and accessible labels were missing for media file inputs.
6. **Memoization Breaks**: `useUserPosts` and `useProfileUsers` passed inline default objects (`= {}`) creating new references on every render.

## Scope & Tasks
- [x] **TASK-01**: Fix WebSocket effect cleanup in `useChatWebSocket.ts` (consistently return cleanup across branches, avoid cascading setState in effect).
- [x] **TASK-02**: Memoize `AuthContext` provider value with `useMemo` and callbacks with `useCallback` in `AuthContext.tsx`.
- [x] **TASK-03**: Add `URL.revokeObjectURL` cleanup on cancelled fetches in `AuthImage.tsx`.
- [x] **TASK-04**: Wrap loading state resets unconditionally in `finally` blocks in `useFeed.ts` and `ChatPage.tsx`.
- [x] **TASK-05**: Enhance accessibility (keyboard handlers for clicks, accessible labels) in `Avatar.tsx`, `ConversationList.tsx`, `NotificationItem.tsx`, `PostGridItem.tsx`, `ChatInputBar.tsx`, and `CreatePostCard.tsx`.
- [x] **TASK-06**: Fix default parameter object references in `useUserPosts.ts` and `useProfileUsers.ts` (`DEFAULT_PARAMS`).
- [x] **TASK-07**: Verify full test suite (`pnpm test`), lint (`pnpm lint`), format (`pnpm format:check`), and build (`pnpm build`).

## Acceptance Criteria & Evidence
- 100% of Vitest tests pass: 48/48 test files passed (265/265 tests).
- ESLint: 0 errors, 0 warnings.
- Prettier: 100% clean formatting.
- Vite / TypeScript build: 100% success.
