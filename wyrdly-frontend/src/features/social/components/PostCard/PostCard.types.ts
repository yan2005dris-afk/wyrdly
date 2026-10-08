import type { Post, ReactionType } from "../../../../types/feed";

export interface PostCardProps {
  readonly post: Post;
  /**
   * Fired when the user clicks any reaction-shaped button (LIKE / LOVE /
   * CELEBRATE / RETWEET). The consumer is responsible for calling the
   * backend and applying optimistic / rollback state via useReaction +
   * useFeed.
   */
  readonly onReaction?: (postId: string, reaction: ReactionType) => void;
  readonly onCommentClick?: (postId: string) => void;
  readonly onShareClick?: (postId: string) => void;
  readonly className?: string;
  /**
   * When true, the three reaction buttons (LIKE / LOVE / CELEBRATE) are
   * disabled and rendered with `opacity-60 cursor-wait` plus `aria-busy`.
   * Driven by useReaction.isPending(postId) in FeedPage.
   */
  readonly isReactionPending?: boolean;
  readonly defaultCommentsOpen?: boolean;
}
