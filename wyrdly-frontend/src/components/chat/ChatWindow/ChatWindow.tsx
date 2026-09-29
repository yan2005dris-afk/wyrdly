import { useRef, useEffect, type FC } from "react";
import type { ChatWindowProps } from "./ChatWindow.types";
import { ChatHeader } from "../ChatHeader";
import { MessageBubble } from "../MessageBubble";
import { ChatInputBar } from "../ChatInputBar";
import styles from "./ChatWindow.module.css";

export const ChatWindow: FC<ChatWindowProps> = ({
  conversation,
  currentUserId,
  messages,
  isSending = false,
  onSendMessage,
  onCallClick,
  onVideoClick,
  onOptionsClick,
  className = "",
}) => {
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  return (
    <main
      className={`${styles.windowContainer} ${className}`}
      data-testid="chat-window"
    >
      <ChatHeader
        participant={conversation.participant}
        isOnline={conversation.isOnline}
        statusText={
          conversation.typingStatus ||
          (conversation.isOnline
            ? "Online • Connected via WebSocket"
            : "Offline")
        }
        onCallClick={onCallClick}
        onVideoClick={onVideoClick}
        onOptionsClick={onOptionsClick}
      />

      <div className={styles.messagesThread} data-testid="chat-messages-thread">
        <div className={styles.encryptionBadgeRow}>
          <span className={styles.encryptionBadge}>
            Today • E2E Encrypted over WebSocket
          </span>
        </div>

        {messages.map((msg) => {
          const isOutgoing = msg.senderId === currentUserId;
          return (
            <MessageBubble
              key={msg.id}
              message={msg}
              isOutgoing={isOutgoing}
              senderAvatarUrl={
                isOutgoing ? undefined : conversation.participant.avatarUrl
              }
              senderName={
                isOutgoing ? "You" : conversation.participant.fullName
              }
            />
          );
        })}
        <div ref={messagesEndRef} />
      </div>

      <ChatInputBar
        recipientName={conversation.participant.fullName.split(" ")[0]}
        isSending={isSending}
        onSend={onSendMessage}
      />
    </main>
  );
};
