# Feature: Simplify UserSummaryCard in Sidebar and Optimize Navigation Layout (Issue #18)

## Objective
Refactor `UserSummaryCard` in the desktop sidebar to remove redundant stats/KPIs (Followers, Following, Posts), focusing the card on user identity. Ensure profile statistics remain prominently in `ProfileHeaderCard` on the `/profile` page, optimize the freed sidebar vertical space for navigation and quick actions, and update all affected tests.

## Scope
- `wyrdly-frontend/src/features/social/components/UserSummaryCard/UserSummaryCard.tsx`: Add `showStats?: boolean` (default `false`), add quick profile navigation shortcut.
- `wyrdly-frontend/src/features/social/components/UserSummaryCard/UserSummaryCard.types.ts`: Update prop types.
- `wyrdly-frontend/src/features/social/components/UserSummaryCard/UserSummaryCard.module.css`: Adjust card padding, spacing, and interactive styling.
- `wyrdly-frontend/src/components/layout/MainLayout/MainLayout.tsx` & `SidebarNav.module.css`: Optimize sidebar navigation link sizing and ergonomics with the reclaimed vertical space.
- `UserSummaryCard.test.tsx`, `ProfileHeaderCard.test.tsx`, `MainLayout.test.tsx`: Update unit test assertions.

## Tasks
- [x] TASK-1: Refactor `UserSummaryCard` to support `showStats?: boolean` (defaulting to `false`), streamlining the card to focus cleanly on user identity.
- [x] TASK-2: Optimize sidebar layout in `MainLayout` and `SidebarNav` to make best use of the reclaimed vertical space for navigation and actions.
- [x] TASK-3: Update tests in `UserSummaryCard.test.tsx`, `ProfileHeaderCard.test.tsx`, and `MainLayout.test.tsx`.
- [x] TASK-4: Run full quality and verification checks (Vitest, ESLint, Prettier, TypeScript build).

## Verification Evidence
- Vitest: 47/47 test files passed (242/242 tests passed).
- ESLint: `pnpm run lint` passed with 0 errors and 0 warnings.
- Prettier: `pnpm run format:check` 100% formatted.
- Build: `pnpm run build` (`tsc -b && vite build`) passed with exit code 0.
