import type { FC } from "react";
import { Check, CheckCheck } from "lucide-react";
import type { MessageBubbleProps } from "./MessageBubble.types";
import { Avatar } from "../../ui/Avatar";
import styles from "./MessageBubble.module.css";

export const MessageBubble: FC<MessageBubbleProps> = ({
  message,
  isOutgoing,
  senderAvatarUrl,
  senderName = "User",
  className = "",
}) => {
  const renderDeliveryIcon = () => {
    switch (message.deliveryStatus) {
      case "READ":
        return (
          <CheckCheck
            className={`${styles.statusIcon} text-indigo-500`}
            data-testid="status-read"
          />
        );
      case "DELIVERED":
        return (
          <CheckCheck
            className={styles.statusIcon}
            data-testid="status-delivered"
          />
        );
      case "SENT":
        return (
          <Check className={styles.statusIcon} data-testid="status-sent" />
        );
      case "SENDING":
      default:
        return null;
    }
  };

  return (
    <div
      className={`${styles.wrapper} ${
        isOutgoing ? styles.outgoing : styles.incoming
      } ${className}`}
      data-testid={`message-bubble-${message.id}`}
    >
      {!isOutgoing && (
        <Avatar src={senderAvatarUrl} alt={senderName} size="xs" />
      )}

      <div>
        <div
          className={`${styles.bubble} ${
            isOutgoing ? styles.bubbleOutgoing : styles.bubbleIncoming
          }`}
          data-testid="bubble-content"
        >
          {message.text}
        </div>

        <div
          className={`${styles.metaRow} ${
            isOutgoing ? styles.metaOutgoing : styles.metaIncoming
          }`}
        >
          <span>{message.timestamp}</span>
          {isOutgoing && renderDeliveryIcon()}
        </div>
      </div>
    </div>
  );
};
