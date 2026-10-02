# Feature: Frontend Feed and Profile Skeleton Loading and Cleanup

## Objective
Implement skeleton loading states across `FeedPage` (timeline posts and graph suggestions) and `ProfilePage` (profile header card, posts grid, and followers/following user rows), replacing blank spaces and intrusive loading spinners with seamless wireframe placeholders using the reusable `Skeleton` component. Purge residual mock fallbacks in `FeedPage`.

## Scope
- Social skeletons: `PostCardSkeleton` in `src/features/social/components/PostCard/` and skeleton rows in `GraphSuggestionsCard`.
- Profile skeletons: `ProfileHeaderSkeleton`, `PostGridItemSkeleton`, and `UserListRowSkeleton` in `src/features/profile/` and `src/features/social/`.
- Integration into `FeedPage.tsx` and `ProfilePage.tsx`.
- Mock cleanup in `FeedPage.tsx` (`currentUserSummary` fallback without invalid properties).
- Tests in `FeedPage.test.tsx`, `ProfilePage.test.tsx`, and component tests.

## Tasks
- [x] TASK-1: Build `PostCardSkeleton` and add skeleton support to `GraphSuggestionsCard` and `FeedPage.tsx`.
- [x] TASK-2: Build `ProfileHeaderSkeleton`, `PostGridItemSkeleton`, and integrate into `ProfilePage.tsx` tabs.
- [x] TASK-3: Update unit tests for `FeedPage` and `ProfilePage` to assert skeleton placeholders and data states.
- [x] TASK-4: Run full verification suite (tests, linter, formatting, build).

## Verification Evidence
- Vitest: 47/47 files passed (239/239 tests passed).
- Linting: `pnpm run lint` passed with 0 errors and 0 warnings.
- Formatting: `pnpm run format:check` 100% compliant.
- TypeScript & Build: `pnpm run build` (`tsc -b && vite build`) passed with exit code 0.
