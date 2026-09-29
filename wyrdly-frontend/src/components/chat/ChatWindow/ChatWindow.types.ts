import type { ChatConversation, ChatMessage } from "../../../types/chat";

export interface ChatWindowProps {
  readonly conversation: ChatConversation;
  readonly currentUserId: string;
  readonly messages: readonly ChatMessage[];
  readonly isSending?: boolean;
  readonly onSendMessage: (text: string, file?: File) => Promise<void> | void;
  readonly onCallClick?: () => void;
  readonly onVideoClick?: () => void;
  readonly onOptionsClick?: () => void;
  readonly className?: string;
}
