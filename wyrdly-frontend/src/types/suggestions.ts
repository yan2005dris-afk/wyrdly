import type { NodeId } from "./domain";

export interface GraphSuggestionUser {
  readonly id: NodeId;
  readonly username: string;
  readonly fullName: string;
  readonly avatarUrl: string | null;
  readonly mutualConnectionSnippet: string;
  readonly isFollowing: boolean;
}

/**
 * Lightweight user shape returned by /api/users/{username}/followers
 * and /following. Same identity fields as GraphSuggestionUser minus
 * the FoF-only `mutualConnectionSnippet`, which is meaningless in a
 * direct follower / following list.
 */
export interface ProfileUserSummary {
  readonly id: NodeId;
  readonly username: string;
  readonly fullName: string;
  readonly avatarUrl: string | null;
  readonly isFollowing: boolean;
}

export interface PaginationMeta {
  readonly page: number;
  readonly pageSize: number;
  readonly totalCount: number;
}

export interface GraphSuggestionsResponse {
  readonly data: readonly GraphSuggestionUser[];
  readonly meta: PaginationMeta;
}

export interface GetSuggestionsParams {
  readonly page?: number;
  readonly pageSize?: number;
}

export interface FollowActionResponse {
  readonly message: string;
  readonly targetUserId: string;
  readonly following: boolean;
}
