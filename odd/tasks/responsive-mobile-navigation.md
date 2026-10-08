# Responsive Mobile Navigation and Adaptive Layouts (#130)

## Objective
Implement responsive mobile navigation and layout adaptations across Wyrdly to deliver a seamless mobile (<640px), tablet (640px-1023px), and desktop (>=1024px) experience:
- Mobile primary navigation via persistent `BottomNav` bar (Feed, Explore, Messages with badge, Profile).
- Removal of top-stacked desktop sidebar on screens <1024px in `MainLayout`.
- Mobile master-detail flow in `ChatPage` (list vs. active chat with Back button).
- Mobile-friendly `AppNavbar` and responsive grid refinements for `FeedPage` and `ProfilePage`.

## Scope & Constraints
- Screaming / Clean Architecture in frontend (`components/layout/BottomNav`, `MainLayout`, `AppNavbar`, `pages/ChatPage`).
- Maintain accessibility (ARIA roles, keyboard navigation, touch targets >= 44px).
- Zero regression on existing 345 test cases; add unit tests for `BottomNav` and mobile layout states.
- Clean Tailwind and CSS modules, respecting existing styling guidelines.

## Tasks
- [x] `TASK-1`: Components - Implement `BottomNav` component (`BottomNav.tsx`, `BottomNav.module.css`, `BottomNav.types.ts`, `BottomNav.test.tsx`) with active state and unread badge.
- [x] `TASK-2`: Layout - Refactor `MainLayout` to hide bulky desktop sidebar on `< 1024px` and render sticky `BottomNav` on mobile screens with bottom safe-area padding.
- [x] `TASK-3`: Navigation - Optimize `AppNavbar` for mobile viewports (`< 640px`), ensuring search bar and brand elements do not overflow or squeeze controls.
- [x] `TASK-4`: Chat UX - Implement responsive master-detail pattern in `ChatPage` (display conversation list OR active chat with back button on `< 1024px`).
- [x] `TASK-5`: Pages - Refine responsive grid and sidebar stacking for `FeedPage` and `ProfilePage`.
- [x] `TASK-6`: Verification - Full Vitest test suite execution, TypeScript check, and ESLint validation.

## Verification Evidence
- Vitest suite: 57 test files, 350 tests passing (including 4 new BottomNav tests, 1 updated MainLayout test, 1 new ChatPage master-detail mobile test).
- TypeScript check (`tsc -b`): Passed cleanly with 0 errors.
- Vite build (`vite build`): Succeeded (assets generated at dist/).
- ESLint (`eslint .`): Passed with 0 errors/warnings.
