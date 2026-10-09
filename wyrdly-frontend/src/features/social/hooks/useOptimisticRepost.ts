import { useCallback } from "react";
import type { Post } from "../../../types/feed";
import { useRepost } from "./useRepost";
import { withRepost } from "../utils/repostState";

export interface UseOptimisticRepostOptions {
  /** Reads the current version of a post from the caller's store. */
  readonly findPost: (postId: string) => Post | undefined;
  /** Applies a functional update to a post in the caller's store. */
  readonly updatePost: (postId: string, updater: (post: Post) => Post) => void;
}

export interface UseOptimisticRepostReturn {
  readonly toggle: (postId: string) => void;
  readonly isPending: (postId: string) => boolean;
}

/**
 * Optimistic repost toggle reconciled with the backend (HU #144).
 *
 * Steps:
 * 1. Optimistic update: toggle `isReposted` and adjust `repostsCount` (+1 / -1).
 * 2. Server mutation: calls `setRepost(postId, next)`.
 * 3. Server confirmation: reconciles with authoritative `response.reposted`.
 * 4. Failure: rolls back to the previous state.
 */
export function useOptimisticRepost({
  findPost,
  updatePost,
}: UseOptimisticRepostOptions): UseOptimisticRepostReturn {
  const { repost, isPending } = useRepost();

  const toggle = useCallback(
    (postId: string) => {
      const post = findPost(postId);
      if (!post) return;

      const previous = Boolean(post.isReposted);
      const next = !previous;

      const moveTo = (reposted: boolean) =>
        updatePost(postId, (current) => withRepost(current, reposted));

      moveTo(next);

      void repost(postId, next, {
        onServerResult: (response) => {
          moveTo(response.reposted);
        },
        onRollback: () => {
          moveTo(previous);
        },
      });
    },
    [findPost, updatePost, repost],
  );

  return { toggle, isPending };
}
