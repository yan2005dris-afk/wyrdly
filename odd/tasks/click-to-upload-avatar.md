# Feature: Click-to-Upload Avatar in Profile Edit

- **Feature Name**: `click-to-upload-avatar`
- **Objective**: Replace the manual Avatar URL text input in `EditProfileModal` with an interactive Click-to-Upload avatar picker pattern (DOM hidden file input triggered by avatar click with instant preview and backend upload).
- **Why**: Asking users to paste an image URL is cumbersome and poor UX; clicking the default or current avatar with an overlay to pick an image file directly (.png, .jpeg, .webp, .gif) provides a modern social network experience aligned with ADR-003 and existing media upload capabilities.
- **Scope**:
  - `wyrdly-frontend/src/components/profile/EditProfileModal/EditProfileModal.tsx`
  - `wyrdly-frontend/src/components/profile/EditProfileModal/EditProfileModal.module.css`
  - `wyrdly-frontend/src/components/profile/EditProfileModal/EditProfileModal.types.ts`
  - `wyrdly-frontend/src/components/profile/EditProfileModal/EditProfileModal.test.tsx`
- **Constraints**:
  - Keep ADR-003 compliance: use existing `useMediaUpload` / `mediaApi.upload` which converts raster images to WebP and enforces 10MB limits.
  - Maintain accessibility: keyboard navigable (`role="button"`, `tabIndex={0}`, Enter/Space triggers file picker), descriptive `aria-label`.
  - Responsive and clean UI: camera/edit overlay on avatar hover/focus, spinner during upload, inline error messages.
  - Conventional commits without AI attribution.
- **TDD Mode**: Disabled (functional checks with Vitest specs and linting).
- **Delivery Strategy**: `single-pr` / work-unit commits per task.
- **Mirror**: Pending (Engram MCP server unavailable).

## Tasks

- [x] `TASK-1`: Design and implement `AvatarPicker` interaction inside `EditProfileModal` (custom trigger for hidden file input with camera overlay and instant local preview).
- [x] `TASK-2`: Wire upload lifecycle (`useMediaUpload` integration, upload loading state, error display, form submit payload).
- [x] `TASK-3`: Add Vitest test suite for click-to-upload avatar behavior and verify full test suite passes.

## Verification Evidence

- 8/8 unit tests passing in `EditProfileModal.test.tsx` (avatar trigger click, keyboard access with Enter/Space, file input change, instant preview, successful upload to onSave, upload error rendering, loading button disable).
- 216/216 total unit tests passing in `wyrdly-frontend` (`pnpm test`).
- Linter clean (`eslint .`).
- Production build succeeded (`tsc -b && vite build`).
