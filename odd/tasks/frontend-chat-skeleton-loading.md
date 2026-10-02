# Feature: Frontend Chat Skeleton Loading and Mock Cleanup

## Objective
Remove hardcoded mock contacts and messages (`MOCK_PARTICIPANTS`, `INITIAL_CONVERSATIONS`, `INITIAL_MESSAGES`) from `ChatPage.tsx`, integrate authentic loading states with reusable `Skeleton` UI components, and handle empty states gracefully when a user has no active conversations or messages.

## Problem & Context
The chat frontend was initially populated with static fallback mocks (Alice Chen, Jonas Weber, hardcoded mock messages). In a real environment or when a logged-in user has no followed users, these mock contacts still appeared or replaced empty states, causing confusion. Additionally, when conversation lists or chat message threads are fetching over the network, there was no visual feedback or skeleton placeholder, resulting in jarring layout shifts and poor UX.

## Scope
- Reusable `Skeleton` component in `src/components/ui/Skeleton/`.
- Conversation list skeleton loading state in `ConversationList` component.
- Message thread skeleton loading state in `ChatWindow` component.
- Removal of hardcoded mock fallbacks in `ChatPage.tsx` and replacement with real loading and empty states.
- Unit and integration tests for `Skeleton`, `ConversationList`, `ChatWindow`, and `ChatPage`.

## Tasks
- [x] TASK-1: Create reusable `Skeleton` UI component (`src/components/ui/Skeleton/`) with tests and story/CSS module.
- [x] TASK-2: Update `ConversationList` and `ChatWindow` to support `isLoading` props and render skeleton placeholders.
- [x] TASK-3: Refactor `ChatPage.tsx` to eliminate hardcoded mock contacts/messages, integrate `isLoadingConversations` and `isLoadingMessages`, and display proper empty states.
- [x] TASK-4: Update test suite in `ChatPage.test.tsx` and related component tests to verify loading skeletons and empty states.
- [x] TASK-5: Run linter, prettier, and tests to ensure 100% clean check.

## Verification Evidence
- `pnpm test src/components/ui/Skeleton/Skeleton.test.tsx`: 4/4 passed.
- `pnpm test src/features/chat/components/ConversationList/ConversationList.test.tsx`: 4/4 passed.
- `pnpm test src/features/chat/components/ChatWindow/ChatWindow.test.tsx`: 3/3 passed.
- `pnpm test src/pages/ChatPage.test.tsx`: 6/6 passed.
- `pnpm test`: 47/47 files passed (234/234 tests).
- `pnpm run lint`: 0 errors, 0 warnings.
- `pnpm run format:check`: 100% formatted.
- `pnpm run build`: `tsc -b && vite build` built successfully.
