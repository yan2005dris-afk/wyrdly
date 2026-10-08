# ADR-007: Gradual Migration of Social Module to TanStack Query

**Status:** Accepted  
**Date:** 2026-10-08  
**Deciders:** Fullstack Team  
**Related:** [post-comments-feature.md](../../odd/tasks/post-comments-feature.md), [ADR-004](ADR-004-frontend-screaming-feature-sliced-architecture.md)

---

## Context

The Wyrdly frontend stack already includes and configures `@tanstack/react-query: ^5.104.1` in `package.json` and wraps the entire application tree in `src/App.tsx` with `<QueryClientProvider client={queryClient}>`. The `notifications` module (`useNotifications.ts`) actively uses TanStack Query for remote state synchronization, cache invalidation, and optimistic mutations.

However, the `social` module (`src/features/social`) currently contains legacy state management patterns:
- `useFeed.ts` uses manual `useState` + `useEffect` and custom cursor pagination.
- `useReaction.ts` uses manual `useState` with `AbortController` cancellation for in-flight requests.

With the introduction of the Post Comments feature (Issue #122), we need a robust solution for fetching, caching, creating, and deleting comments per post.

Three options were evaluated:

1. **Option 1 (Gradual migration — Chosen):** Implement `useComments` with TanStack Query (`queryKey: ["comments", postId]`), annotate existing hooks with `TODO(hu-migrate-social-to-tanstack)`, and migrate `useReaction` and `useFeed` in isolated, dedicated follow-up tickets.
2. **Option 2 (Big bang / Atomic migration in this PR):** Migrate `useFeed`, `useReaction`, and implement `useComments` all in the same PR.
3. **Option 3 (Stick to manual state in social):** Implement `useComments` with manual `useState`, `AbortController`, and a custom `Map<postId, CommentState>` registry.

---

## Decision

**Adopt Option 1.**

### Rationale

1. **Zero regression blast radius:** `useFeed` controls core feed loading, pagination, and interactions across dozens of components and tests. Migrating `useFeed` in the same PR as the new comments feature would mix two distinct architectural goals ("add post comments" vs "refactor feed state"), drastically complicating code review, debugging, and git bisect.
2. **TanStack Query is the project's target standard:** The root application and the notifications module already standardize on TanStack Query. Reinventing query caching, retry logic, and background invalidation manually in `useComments` would be counterproductive technical debt.
3. **Per-post cache isolation:** TanStack Query handles independent query cache keys (`["comments", postId]`) out of the box, avoiding prop-drilling or maintaining custom state dictionaries in parent components.

---

## Migration Roadmap

1. **Phase 1 (Current - Issue #122):**
   - Implement `useComments.ts` with TanStack Query (`useQuery` and `useMutation`).
   - Add `// TODO(hu-migrate-social-to-tanstack): see ADR-007` to `useFeed.ts`, `useReaction.ts`, and `useComments.ts`.
2. **Phase 2 (Follow-up ticket):**
   - Migrate `useReaction.ts` to `useMutation` (low risk, single interaction).
3. **Phase 3 (Follow-up ticket):**
   - Migrate `useFeed.ts` to `useInfiniteQuery` with dedicated regression tests.

---

## Consequences

### Positive
- Issue #122 remains strictly scoped to post comments.
- Comments benefit from declarative query caching, automatic refetching, and clean mutation invalidation.
- The migration path is clear, documented, and traceable.

### Negative
- Temporary coexistence of TanStack Query (`useComments`) and manual `useState` (`useFeed`, `useReaction`) within `features/social`. This is mitigated by explicit TODO comments referencing this ADR.
