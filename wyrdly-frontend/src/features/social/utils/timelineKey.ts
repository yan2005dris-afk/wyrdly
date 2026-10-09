import type { Post } from "../../../types/feed";

/**
 * Stable React key for a timeline entry. A share is keyed by reposter + post
 * so a timeline can hold both the original and someone's share of the same
 * post without key collisions (HU #150).
 */
export function getTimelineItemKey(
  post: Pick<Post, "id" | "repostContext">,
): string {
  return post.repostContext
    ? `repost:${post.repostContext.reposterId}:${post.id}`
    : post.id;
}
