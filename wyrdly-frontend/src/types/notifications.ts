import type { ISO8601Timestamp, UserProfileSummary } from "./domain";

export type NotificationType =
  "POST_LIKE" | "POST_BOOST" | "GRAPH_FOLLOW" | "CHAT_MESSAGE";

export interface SocialNotification {
  readonly id: string;
  readonly type: NotificationType;
  readonly actor: UserProfileSummary;
  readonly message: string;
  readonly targetResourceId?: string;
  readonly targetSnippet?: string;
  readonly createdAt: ISO8601Timestamp;
  readonly isRead: boolean;
}

export type PushPermissionStatus = "default" | "granted" | "denied";

export interface PushSubscriptionConfig {
  readonly vapidPublicKey: string;
  readonly endpoint?: string;
}
