import type { Post, ReactionType } from "../../../types/feed";

export interface PostCardProps {
  readonly post: Post;
  readonly onReaction?: (postId: string, reaction: ReactionType) => void;
  readonly onCommentClick?: (postId: string) => void;
  readonly onShareClick?: (postId: string) => void;
  readonly className?: string;
}
