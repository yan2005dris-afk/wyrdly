export interface UserSearchResult {
  readonly id: string;
  readonly username: string;
  readonly fullName: string;
  readonly avatarUrl: string | null;
  readonly bio: string | null;
  readonly isFollowing: boolean;
  readonly mutualConnectionSnippet: string | null;
}

export interface UserSearchMeta {
  readonly page: number;
  readonly pageSize: number;
  readonly totalResults: number;
}

export interface UserSearchResponse {
  readonly data: readonly UserSearchResult[];
  readonly meta: UserSearchMeta;
}