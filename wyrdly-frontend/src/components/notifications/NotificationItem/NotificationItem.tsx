import type { FC } from "react";
import { Heart, Repeat, UserPlus, MessageSquare } from "lucide-react";
import type { NotificationItemProps } from "./NotificationItem.types";
import type { NotificationType } from "../../../types/notifications";
import { Avatar } from "../../ui/Avatar";
import styles from "./NotificationItem.module.css";

const TYPE_CONFIG: Record<
  NotificationType,
  { readonly badgeClass: string; readonly icon: React.ReactElement }
> = {
  POST_LIKE: {
    badgeClass: styles.badgeLike,
    icon: <Heart className="w-2.5 h-2.5 fill-current" />,
  },
  POST_BOOST: {
    badgeClass: styles.badgeBoost,
    icon: <Repeat className="w-2.5 h-2.5" />,
  },
  GRAPH_FOLLOW: {
    badgeClass: styles.badgeFollow,
    icon: <UserPlus className="w-2.5 h-2.5" />,
  },
  CHAT_MESSAGE: {
    badgeClass: styles.badgeChat,
    icon: <MessageSquare className="w-2.5 h-2.5" />,
  },
};

export const NotificationItem: FC<NotificationItemProps> = ({
  notification,
  onClick,
  className = "",
}) => {
  const config = TYPE_CONFIG[notification.type];

  return (
    <div
      className={`${styles.item} ${!notification.isRead ? styles.itemUnread : ""} ${className}`}
      onClick={() => onClick?.(notification.id)}
      role="button"
      tabIndex={0}
      data-testid={`notification-item-${notification.id}`}
    >
      <div className={styles.avatarWrapper}>
        <Avatar
          src={notification.actor.avatarUrl}
          alt={notification.actor.fullName}
          size="sm"
        />
        <span
          className={`${styles.typeBadge} ${config.badgeClass}`}
          data-testid={`type-badge-${notification.type}`}
        >
          {config.icon}
        </span>
      </div>

      <div className={styles.contentCol}>
        <p className={styles.message}>
          <strong className={styles.actorName}>
            {notification.actor.fullName}
          </strong>{" "}
          <span>{notification.message}</span>{" "}
          {notification.targetSnippet && (
            <span className={styles.targetSnippet}>
              "{notification.targetSnippet}"
            </span>
          )}
        </p>
        <span className={styles.timestamp}>{notification.createdAt}</span>
      </div>
    </div>
  );
};
