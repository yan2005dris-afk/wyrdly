import type { Post } from "../../../types/feed";

/** Repost-related slice of a Post. */
export type RepostState = Pick<Post, "repostsCount" | "isReposted">;

/**
 * Pure function that returns a new post/repost state with the repost status
 * set to `reposted`:
 * - If `state.isReposted === reposted`, returns the same reference (no-op, avoids re-renders).
 * - If `reposted` is true, increments `repostsCount` by 1 and sets `isReposted: true`.
 * - If `reposted` is false, decrements `repostsCount` by 1 (clamped to 0) and sets `isReposted: false`.
 *
 * Single source of truth for optimistic repost updates, server reconciliation, and rollback.
 */
export function withRepost<T extends RepostState>(
  state: T,
  reposted: boolean,
): T {
  if (state.isReposted === reposted) {
    return state;
  }

  const nextCount = reposted
    ? state.repostsCount + 1
    : Math.max(0, state.repostsCount - 1);

  return {
    ...state,
    repostsCount: nextCount,
    isReposted: reposted,
  };
}
