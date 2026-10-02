import type {
  NodeId,
  ISO8601Timestamp,
  UserProfileSummary,
} from "../../../types/domain";

export type DeliveryStatus = "SENDING" | "SENT" | "DELIVERED" | "READ";

export interface ChatMessage {
  readonly id: string;
  readonly senderId: NodeId;
  readonly recipientId: NodeId;
  readonly text: string;
  readonly timestamp: ISO8601Timestamp;
  readonly deliveryStatus: DeliveryStatus;
  readonly isEncrypted: boolean;
  readonly attachmentUrl?: string;
}

export interface ChatConversation {
  readonly id: string;
  readonly participant: UserProfileSummary;
  readonly lastMessage?: ChatMessage;
  readonly unreadCount: number;
  readonly isOnline: boolean;
  readonly typingStatus?: string;
}

export interface SendMessagePayload {
  readonly recipientId: NodeId;
  readonly text: string;
  readonly isEncrypted?: boolean;
}

export interface WebSocketLatencyInfo {
  readonly isConnected: boolean;
  readonly latencyMs: number;
  readonly connectedRelay: string;
}

export interface MessageResponse {
  id: string;
  senderId: string;
  recipientId: string;
  content: string;
  sentAt: string;
}

export interface ChatHistoryMeta {
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
}

export interface ChatHistoryResponse {
  data: MessageResponse[];
  meta: ChatHistoryMeta;
}

export interface WebSocketIncomingMessage {
  action:
    | "CONNECTION_ESTABLISHED"
    | "MESSAGE_SENT"
    | "NEW_MESSAGE"
    | "MESSAGE_RECEIVED"
    | "USER_TYPING"
    | "ERROR";
  message?: MessageResponse | string;
  userId?: string;
}
