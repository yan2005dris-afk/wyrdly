import { useRef, useEffect, type FC } from "react";
import type { ChatWindowProps } from "./ChatWindow.types";
import { ChatHeader } from "../ChatHeader";
import { MessageBubble } from "../MessageBubble";
import { ChatInputBar } from "../ChatInputBar";
import { Skeleton } from "../../../../components/ui/Skeleton";
import styles from "./ChatWindow.module.css";

export const ChatWindow: FC<ChatWindowProps> = ({
  conversation,
  currentUserId,
  messages,
  isLoadingMessages = false,
  isSending = false,
  onSendMessage,
  onBack,
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
          (conversation.isOnline ? "Online" : "Offline")
        }
        onBack={onBack}
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

        {isLoadingMessages ? (
          <div
            data-testid="chat-messages-loading"
            className="flex flex-col gap-3 py-2"
          >
            <div
              className="flex items-start gap-2.5 max-w-[70%] mr-auto"
              data-testid="message-skeleton-incoming"
            >
              <Skeleton variant="circular" width={32} height={32} />
              <div className="flex flex-col gap-1.5 flex-1">
                <Skeleton variant="rounded" width={180} height={42} />
              </div>
            </div>
            <div
              className="flex items-end justify-end gap-2.5 max-w-[60%] ml-auto"
              data-testid="message-skeleton-outgoing"
            >
              <div className="flex flex-col items-end gap-1.5 flex-1">
                <Skeleton variant="rounded" width={140} height={36} />
              </div>
            </div>
            <div
              className="flex items-start gap-2.5 max-w-[75%] mr-auto"
              data-testid="message-skeleton-incoming"
            >
              <Skeleton variant="circular" width={32} height={32} />
              <div className="flex flex-col gap-1.5 flex-1">
                <Skeleton variant="rounded" width={220} height={52} />
              </div>
            </div>
            <div
              className="flex items-end justify-end gap-2.5 max-w-[60%] ml-auto"
              data-testid="message-skeleton-outgoing"
            >
              <div className="flex flex-col items-end gap-1.5 flex-1">
                <Skeleton variant="rounded" width={160} height={36} />
              </div>
            </div>
          </div>
        ) : messages.length === 0 ? (
          <div
            data-testid="chat-messages-empty"
            className="flex flex-col items-center justify-center py-12 text-slate-400 text-sm"
          >
            <p>No messages yet in this conversation.</p>
            <p className="text-xs text-slate-400 mt-1">
              Send a message below to start chatting!
            </p>
          </div>
        ) : (
          messages.map((msg) => {
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
          })
        )}
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
