import type { Post } from "../../../types/feed";

/**
 * True when the profile owner, viewing their own profile, has just undone
 * the repost that put this entry in their timeline (HU #150). Used to remove
 * the entry optimistically; the backend drops it on the next fetch anyway.
 *
 * `isReposted` is the VIEWER's state, so this only applies when the viewer
 * is the profile owner — otherwise a visitor who never reposted would hide
 * every share in someone else's timeline.
 */
export function isHiddenFromOwnerTimeline(
  post: Pick<Post, "isReposted" | "repostContext">,
  ownerId: string | undefined,
  viewerId: string | undefined,
): boolean {
  if (!ownerId || ownerId !== viewerId) return false;
  return post.repostContext?.reposterId === ownerId && !post.isReposted;
}
