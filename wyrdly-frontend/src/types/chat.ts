import type { NodeId, ISO8601Timestamp, UserProfileSummary } from "./domain";

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
