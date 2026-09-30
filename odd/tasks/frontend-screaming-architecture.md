# Feature: Frontend Screaming & Feature-Sliced Architecture Refactor

- **Feature Name**: `frontend-screaming-architecture`
- **Objective**: Refactor `wyrdly-frontend` from a layered technical directory layout (`src/components/`, `src/hooks/`, `src/api/`) into a domain-driven Screaming / Feature-Sliced Architecture (`src/features/<module>/` with encapsulated `components/`, `hooks/`, `api/`, `types/`, and public API `index.ts` barrels).
- **Why**: Aligns frontend with ADR-004. Increases cohesion, prevents cross-feature coupling, and provides clean encapsulation as the social network platform scales.
- **Scope**:
  - `wyrdly-frontend/src/features/` (new)
  - `wyrdly-frontend/src/components/` (retain only `ui/` design system and `layout/` shell)
  - `wyrdly-frontend/src/pages/` (updated to consume feature public APIs)
  - `wyrdly-frontend/src/hooks/`, `src/api/`, `src/types/` (scoped into features or retained as shared primitives)
- **Constraints**:
  - Keep 100% test coverage and ensure all 216+ Vitest specs pass without regressions at each step.
  - Strict public API barrels: consumer pages and external features must only import from `src/features/<module>` (no deep private imports).
  - Conventional commits per work unit, no AI attribution.
- **TDD Mode**: Disabled (functional checks with Vitest, ESLint, and Vite build).
- **Delivery Strategy**: `single-pr` / work-unit commits per feature slice.
- **Mirror**: Pending (Engram MCP server unavailable).

## Tasks

- [x] `TASK-1`: Migrate `features/auth/` (encapsulate `AuthFormCard`, `AuthGraphHero`, `useAuth`, `auth` types, public API barrel export, update `AuthPage` and tests).
- [x] `TASK-2`: Migrate `features/profile/` (encapsulate `EditProfileModal`, `ProfileHeaderCard`, `PostGridItem`, `useUserProfile`, `useProfileUsers`, profile types/APIs, public API barrel, update `ProfilePage` and tests).
- [x] `TASK-3`: Migrate `features/social/` (encapsulate `PostCard`, `CreatePostCard`, `GraphSuggestionsCard`, `UserListRow`, `UserSearchResultCard`, `UserSummaryCard`, `RelayHealthWidget`, social hooks/APIs, public API barrel, update `FeedPage` and tests).
- [x] `TASK-4`: Migrate `features/chat/` (encapsulate `ChatWindow`, `ChatInputBar`, `ConversationList`, `MessageBubble`, `ChatHeader`, chat types, public API barrel, update `ChatPage` and tests).
- [ ] `TASK-5`: Migrate `features/notifications/` (encapsulate `NotificationPopover`, `NotificationItem`, `PushPermissionBanner`, notifications types, public API barrel, update consumers and tests).
- [ ] `TASK-6`: Clean up deprecated root directories/imports, run complete validation suite (Vitest 216+ specs, ESLint, Prettier, Vite production build).
