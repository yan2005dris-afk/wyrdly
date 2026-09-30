# Feature: UserListRow reusable (follow/unfollow on followers/following)

## Problem
`FollowersOrFollowingTab` in ProfilePage shows a follower / following
row with a static "Following" / "Not following" status badge. The
user can see whether they follow that person, but cannot act on it
from the same row. They have to navigate to that user's profile to
follow or unfollow. Friction.

`GraphSuggestionsCard` already has a Follow/Following button in
each row, but the click handler is owned by the parent — the row is
not self-contained. There is no shared component.

## Solution
New reusable `UserListRow` component that:
- Renders the user identity (avatar + full name + username) with a
  link to their profile.
- Owns a Follow / Following toggle button that calls `useFollow()`.
  Optimistic state: the button reflects the new state immediately;
  on API error it rolls back to the original `isFollowing` value and
  surfaces a console error.
- Is parameterised by an optional `subtitle` (used by Smart
  Suggestions to show the mutual-connection snippet) so the same
  component covers followers, following and suggestions.

## Scope
- **NEW** `src/components/social/UserListRow/UserListRow.tsx` (and
  types/test).
- `ProfilePage.FollowersOrFollowingTab` swaps its bespoke row for
  `<UserListRow>`.
- `GraphSuggestionsCard` swaps its bespoke row for `<UserListRow>`
  with `subtitle={user.mutualConnectionSnippet}`. The parent no
  longer needs to provide `onFollowToggle` for that card.
- Tests for the new component plus adjustments on the two consumers.

## Out of scope
- Optimistic update of the parent list when a follow/unfollow
  fires (the list will refresh on next mount; this is a UX nicety
  for a future ticket).
- Optimistic disable of the button while the API is in flight (we
  show no spinner today; useFollow already exposes isMutating but we
  do not need to block — the next-state render wins because we are
  optimistic).
- Other lists (search results, follower-of-follower suggestions).

## Constraints
- Conventional Commits.
- `pnpm ci` clean.
- `tsc --noEmit` clean.
- The component must accept both `string` and `null | undefined`
  for `src` (Avatar accepts `string | undefined`, but the backend
  returns `string | null` for some fields — we coerce internally).

## Tasks
- [x] T1 — Branch + tracking scaffold.
- [x] T2 — UserListRow component + tests.
- [x] T3 — Wire into FollowersOrFollowingTab.
- [x] T4 — Refactor GraphSuggestionsCard to use UserListRow.
- [x] T5 — Validate (vitest 46/46 passed, tsc clean, eslint clean, prettier clean).

## Evidence
- Work-unit commit: `cd682ae` (`feat(social): add reusable UserListRow component with optimistic follow toggle`)
- Vitest: 46 files passed, 211 tests passed
- TypeScript: `tsc -b` exited with code 0
- ESLint: clean
- Prettier: clean