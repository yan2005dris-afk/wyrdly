import type { ChatConversation } from "../../../types/chat";

export interface ConversationItemProps {
  readonly conversation: ChatConversation;
  readonly isActive?: boolean;
  readonly onClick?: (conversationId: string) => void;
  readonly className?: string;
}

export interface ConversationListProps {
  readonly conversations: readonly ChatConversation[];
  readonly activeConversationId?: string;
  readonly searchQuery?: string;
  readonly onSearchChange?: (query: string) => void;
  readonly onSelectConversation: (conversationId: string) => void;
  readonly className?: string;
}
