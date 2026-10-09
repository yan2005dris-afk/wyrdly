import type { Post, ReactionType } from "../../../../types/feed";

export interface PostCardProps {
  readonly post: Post;
  /**
   * Fired when the user clicks a reaction button (LIKE / LOVE / CELEBRATE).
   * The consumer is responsible for calling the backend and applying
   * optimistic / rollback state (see useOptimisticReaction).
   */
  readonly onReaction?: (postId: string, reaction: ReactionType) => void;
  /**
   * Fired when the user clicks the boost button. Kept separate from
   * onReaction so the card does not assume how the backend models boosts
   * (as a RETWEET reaction or as an independent repost). When omitted the
   * button renders disabled.
   */
  readonly onBoost?: (postId: string) => void;
  readonly onCommentClick?: (postId: string) => void;
  readonly onShareClick?: (postId: string) => void;
  readonly className?: string;
  /**
   * When true, the three reaction buttons (LIKE / LOVE / CELEBRATE) are
   * disabled and rendered with `opacity-60 cursor-wait` plus `aria-busy`.
   * Driven by useReaction.isPending(postId) in FeedPage.
   */
  readonly isReactionPending?: boolean;
  readonly isBoostPending?: boolean;
  readonly defaultCommentsOpen?: boolean;
}
