import type { ChatMessage } from "../../../types/chat";

export interface MessageBubbleProps {
  readonly message: ChatMessage;
  readonly isOutgoing: boolean;
  readonly senderAvatarUrl?: string;
  readonly senderName?: string;
  readonly className?: string;
}
