import type {
  ISO8601Timestamp,
  UserProfileSummary,
} from "../../../types/domain";

export type NotificationType =
  | "POST_LIKE"
  | "POST_LOVE"
  | "POST_CELEBRATE"
  | "POST_BOOST"
  | "GRAPH_FOLLOW"
  | "CHAT_MESSAGE"
  | "POST_COMMENT";

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

/** Mirrors backend `NotificationDto`. */
export interface NotificationDto {
  readonly id: string;
  readonly type: NotificationType;
  readonly title: string;
  readonly body: string;
  readonly deepLink: string;
  readonly targetResourceId?: string;
  readonly isRead: boolean;
  readonly createdAt: ISO8601Timestamp;
  readonly actor: {
    readonly id: string;
    readonly username: string;
    readonly fullName: string;
    readonly avatarUrl?: string;
    readonly instanceUrl?: string;
  };
}

/** Mirrors backend `NotificationListResponseDto`. */
export interface NotificationListResponseDto {
  readonly notifications: NotificationDto[];
  readonly unreadCount: number;
  readonly page: number;
  readonly pageSize: number;
  readonly totalElements: number;
}
