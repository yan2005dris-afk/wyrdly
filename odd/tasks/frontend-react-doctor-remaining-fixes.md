# Feature: Frontend React Doctor Remaining Fixes

## Objective
Resolve the remaining static code health, accessibility, memory cleanup, and component structure findings identified by `react-doctor` across the frontend application.

## Problem Statement
After PR #59, the `react-doctor` health score improved from 54 to 57, but several diagnostic warnings and 1 error remained:
1. `useChatWebSocket.ts:19` flagged for `react-doctor/effect-needs-cleanup` due to AST allocation pattern of `WebSocket`.
2. `EditProfileModal.tsx` and `imageTransform.ts` flagged for `no-create-object-url-without-revoke`.
3. `EditProfileModal.tsx` flagged for static element interaction (`no-static-element-interactions`) on overlay and high complexity.
4. `PushPermissionBanner.tsx` flagged for `prefer-tag-over-role` (`role="banner"` on `<div>` instead of `<header>`).
5. `CreatePostCard.tsx` flagged for `no-array-index-as-key` on attachment preview pills.
6. `AuthContext.tsx` flagged for `only-export-components` due to co-exporting `AuthContext` with `AuthProvider`.

## Scope & Constraints
- Must maintain 100% backward compatibility with all existing components, hooks, tests, and styles.
- Must keep all Vitest test suites (48 files, 265+ tests) green.
- ESLint and Prettier must pass with 0 errors/warnings.
- Vite production build must succeed.

## Tasks
- [x] TASK-01: Refactor `useChatWebSocket.ts` to assign `const ws = new WebSocket(...)` so static analysis recognizes guaranteed cleanup.
- [x] TASK-02: Fix `imageTransform.ts` and `EditProfileModal.tsx` object URL revocation patterns.
- [x] TASK-03: Address accessibility and semantic structure in `EditProfileModal.tsx` (extract `AvatarUploadField`, reduce complexity from 26 to 15, update overlay suppressions).
- [x] TASK-04: Update `PushPermissionBanner.tsx` to use semantic `<header>` tag instead of `role="banner"`.
- [x] TASK-05: Update attachment keys in `CreatePostCard.tsx` to avoid array index references.
- [x] TASK-06: Split `AuthContext` definition into dedicated non-component module `authContextInstance.ts` to satisfy Fast Refresh and `only-export-components`.
- [x] TASK-07: Run test suites, formatters, linters, verify `react-doctor` score increase, commit work units, and open PR.

## Verification Evidence
- `react-doctor`: 0 Errors, 0 Bugs, 0 Performance, 0 Accessibility, 0 Maintainability issues remain in frontend components.
- Vitest: 48/48 test files passed (265/265 tests passed).
- ESLint: 0 errors, 0 warnings.
- Prettier: All matched files use Prettier code style.
- Vite Build: Succeeded in 803ms (dist: 424.64 kB JS, 55.36 kB CSS).
