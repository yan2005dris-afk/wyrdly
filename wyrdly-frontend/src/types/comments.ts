import type { UserProfileSummary, ISO8601Timestamp } from "./domain";

export interface Comment {
  readonly id: string;
  readonly postId: string;
  readonly authorId: string;
  readonly content: string;
  readonly createdAt: ISO8601Timestamp;
  readonly author: UserProfileSummary;
}

export interface CommentListResponse {
  readonly data: readonly Comment[];
  readonly totalCount: number;
  readonly page: number;
  readonly pageSize: number;
}
