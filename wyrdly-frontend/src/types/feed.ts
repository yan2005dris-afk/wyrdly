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
}
