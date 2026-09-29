export type NodeId = string;
export type ISO8601Timestamp = string;

export type PresenceStatus = "online" | "offline" | "away";
export type RelayRegion = "eu-west-1" | "us-east-1" | "ap-south-1" | "local";

export interface UserStats {
  readonly followersCount: number;
  readonly followingCount: number;
  readonly postsCount: number;
}

export interface UserProfileSummary {
  readonly id: NodeId;
  readonly username: string;
  readonly fullName: string;
  readonly avatarUrl?: string;
  readonly isVerified: boolean;
  readonly instanceUrl: string;
  readonly bio?: string;
  readonly location?: string;
  readonly website?: string;
  readonly joinedDate?: string;
  readonly homeRelay?: string;
  readonly stats: UserStats;
}

export interface GraphSuggestionUser {
  readonly id: NodeId;
  readonly username: string;
  readonly fullName: string;
  readonly avatarUrl?: string;
  readonly mutualConnectionSnippet: string;
  readonly isFollowing: boolean;
}
