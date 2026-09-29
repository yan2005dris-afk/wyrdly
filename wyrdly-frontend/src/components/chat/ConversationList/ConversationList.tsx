import type { FC } from "react";
import { Search } from "lucide-react";
import type {
  ConversationListProps,
  ConversationItemProps,
} from "./ConversationList.types";
import { Avatar } from "../../ui/Avatar";
import { Input } from "../../ui/Input";
import styles from "./ConversationList.module.css";

export const ConversationItem: FC<ConversationItemProps> = ({
  conversation,
  isActive = false,
  onClick,
  className = "",
}) => {
  return (
    <div
      className={`${styles.item} ${isActive ? styles.itemActive : ""} ${className}`}
      onClick={() => onClick?.(conversation.id)}
      role="button"
      tabIndex={0}
      data-testid={`conversation-item-${conversation.id}`}
    >
      <div className={styles.leftCol}>
        <Avatar
          src={conversation.participant.avatarUrl}
          alt={conversation.participant.fullName}
          size="md"
          isOnline={conversation.isOnline}
        />
        <div className={styles.textCol}>
          <h4 className={styles.contactName}>
            {conversation.participant.fullName}
          </h4>
          <p className={styles.messageSnippet}>
            {conversation.lastMessage?.text || "No messages yet"}
          </p>
        </div>
      </div>

      <div className={styles.rightCol}>
        {conversation.lastMessage && (
          <span className={styles.timestamp}>
            {conversation.lastMessage.timestamp}
          </span>
        )}
        {conversation.unreadCount > 0 && (
          <span
            className={styles.unreadBadge}
            data-testid={`unread-badge-${conversation.id}`}
          >
            {conversation.unreadCount > 9 ? "9+" : conversation.unreadCount}
          </span>
        )}
      </div>
    </div>
  );
};

export const ConversationList: FC<ConversationListProps> = ({
  conversations,
  activeConversationId,
  searchQuery = "",
  onSearchChange,
  onSelectConversation,
  className = "",
}) => {
  const filteredConversations = conversations.filter(
    (c) =>
      c.participant.fullName
        .toLowerCase()
        .includes(searchQuery.toLowerCase()) ||
      c.participant.username
        .toLowerCase()
        .includes(searchQuery.toLowerCase()) ||
      (c.lastMessage?.text &&
        c.lastMessage.text.toLowerCase().includes(searchQuery.toLowerCase())),
  );

  return (
    <aside
      className={`${styles.container} ${className}`}
      data-testid="conversation-list"
    >
      <div className={styles.searchHeader}>
        <h2 className={styles.title}>Messages</h2>
        <Input
          variant="filled"
          placeholder="Search conversations..."
          value={searchQuery}
          onChange={(e) => onSearchChange?.(e.target.value)}
          leftIcon={<Search className="w-3.5 h-3.5 text-slate-400" />}
          data-testid="search-conversations-input"
        />
      </div>

      <div className={styles.scrollArea}>
        {filteredConversations.length > 0 ? (
          filteredConversations.map((conv) => (
            <ConversationItem
              key={conv.id}
              conversation={conv}
              isActive={conv.id === activeConversationId}
              onClick={onSelectConversation}
            />
          ))
        ) : (
          <div className={styles.emptyState}>No conversations found</div>
        )}
      </div>
    </aside>
  );
};
