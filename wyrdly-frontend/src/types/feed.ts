import type { NodeId, ISO8601Timestamp, UserProfileSummary } from "./domain";

export type ReactionType = "LIKE" | "LOVE" | "CELEBRATE" | "RETWEET";

export type PostVisibility = "PUBLIC" | "FEDERATED" | "FOLLOWERS";

export interface PostAttachment {
  readonly id: string;
  readonly url: string;
  readonly storageProvider: "RUSTFS_S3";
  readonly mimeType: string;
  readonly altText?: string;
  readonly width?: number;
  readonly height?: number;
}

export interface PostReactions {
  readonly LIKE: number;
  readonly LOVE: number;
  readonly CELEBRATE: number;
  readonly RETWEET: number;
}

export interface Post {
  readonly id: NodeId;
  readonly author: UserProfileSummary;
  readonly content: string;
  readonly createdAt: ISO8601Timestamp;
  readonly attachments: readonly PostAttachment[];
  readonly reactions: Readonly<PostReactions>;
  readonly userReaction?: ReactionType;
  readonly commentsCount: number;
  readonly visibility: PostVisibility;
}

export interface CreatePostPayload {
  readonly content: string;
  readonly attachments?: readonly File[];
  readonly visibility: PostVisibility;
  readonly mediaUrl?: string;
}

/** Backend response for POST /api/posts. Mirrors PostResponse record. */
export interface PostApiResponse {
  readonly id: NodeId;
  readonly content: string;
  readonly mediaUrl: string | null;
  readonly createdAt: string; // ISO 8601 from backend Instant
  readonly author: {
    readonly id: NodeId;
    readonly username: string;
    readonly fullName: string;
    readonly avatarUrl: string | null;
  };
}

/** Payload sent to POST /api/posts. Mirrors CreatePostRequest record. */
export interface CreatePostApiPayload {
  readonly content: string;
  readonly mediaUrl?: string;
}

/**
 * Map the backend PostResponse into the UI-facing Post shape consumed by
 * PostCard. The backend intentionally omits `reactionCounts` / `userReaction`
 * (HU09), `commentsCount` (HU10) and `visibility` (still client-side only);
 * we fill in safe defaults so PostCard keeps rendering without changes.
 *
 * The backend `author` block is the source of truth — we do NOT reuse
 * `currentUserSummary` because the server has just stamped the canonical
 * identity onto the persisted post.
 */
export function mapPostApiResponseToPost(
  response: PostApiResponse,
  visibility: PostVisibility = "PUBLIC",
): Post {
  return {
    id: response.id,
    author: {
      id: response.author.id,
      username: response.author.username,
      fullName: response.author.fullName,
      avatarUrl: response.author.avatarUrl ?? undefined,
      isVerified: false,
      instanceUrl: "wyrdly.app",
      stats: {
        followersCount: 0,
        followingCount: 0,
        postsCount: 0,
      },
    },
    content: response.content,
    createdAt: response.createdAt,
    attachments: response.mediaUrl
      ? [
          {
            id: `att-${response.id}`,
            url: response.mediaUrl,
            storageProvider: "RUSTFS_S3",
            // Backend doesn't echo the mime type yet — default to image/jpeg.
            // Tracked as a follow-up to surface the actual mime from HU06.
            mimeType: "image/jpeg",
          },
        ]
      : [],
    reactions: { LIKE: 0, LOVE: 0, CELEBRATE: 0, RETWEET: 0 },
    commentsCount: 0,
    visibility,
  };
}
