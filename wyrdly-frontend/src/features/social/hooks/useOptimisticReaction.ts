import { useCallback } from "react";
import type { Post, ReactionType } from "../../../types/feed";
import { useReaction } from "./useReaction";
import { predictToggle, withUserReaction } from "../utils/reactionState";

export interface UseOptimisticReactionOptions {
  /** Reads the current version of a post from the caller's store. */
  readonly findPost: (postId: string) => Post | undefined;
  /** Applies a functional update to a post in the caller's store. */
  readonly updatePost: (postId: string, updater: (post: Post) => Post) => void;
}

export interface UseOptimisticReactionReturn {
  readonly toggle: (postId: string, type: ReactionType) => void;
  readonly isPending: (postId: string) => boolean;
}

/**
 * Optimistic reaction toggle reconciled with the backend (HU09).
 *
 * Every step is a relative move of the user's single reaction applied to the
 * latest post state (see withUserReaction):
 * - optimistic → move to the predicted toggle result.
 * - server     → move to the authoritative `reactionType`; a no-op when the
 *                prediction was right, a correction when it was not.
 * - rollback   → move back to the reaction held before the click.
 *
 * Store-agnostic: the caller injects read/update accessors, so the hook is
 * reusable outside the feed. `totalReactions` from the response is not used:
 * it is an aggregate that also reflects other users' activity and cannot be
 * split per type.
 */
export function useOptimisticReaction({
  findPost,
  updatePost,
}: UseOptimisticReactionOptions): UseOptimisticReactionReturn {
  const { react, isPending } = useReaction();

  const toggle = useCallback(
    (postId: string, type: ReactionType) => {
      const post = findPost(postId);
      if (!post) return;

      const previous = post.userReaction;
      const moveTo = (next: ReactionType | undefined) =>
        updatePost(postId, (current) => withUserReaction(current, next));

      moveTo(predictToggle(previous, type));

      void react(postId, type, {
        onServerResult: (response) => {
          moveTo(response.reactionType ?? undefined);
        },
        onRollback: () => {
          moveTo(previous);
        },
      });
    },
    [findPost, updatePost, react],
  );

  return { toggle, isPending };
}
