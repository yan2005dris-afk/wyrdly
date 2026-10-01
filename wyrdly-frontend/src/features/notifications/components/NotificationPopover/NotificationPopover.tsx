import type { FC } from "react";
import type { NotificationPopoverProps } from "./NotificationPopover.types";
import { NotificationItem } from "../NotificationItem";
import styles from "./NotificationPopover.module.css";

export const NotificationPopover: FC<NotificationPopoverProps> = ({
  notifications,
  onMarkAllAsRead,
  onNotificationClick,
  onViewAllActivity,
  className = "",
}) => {
  return (
    <div
      className={`${styles.container} ${className}`}
      data-testid="notification-popover"
    >
      <div className={styles.header}>
        <h3 className={styles.title}>Notifications</h3>
        {onMarkAllAsRead && (
          <button
            type="button"
            onClick={onMarkAllAsRead}
            className={styles.markReadBtn}
            data-testid="mark-all-read-btn"
          >
            Mark all read
          </button>
        )}
      </div>

      <div className={styles.list}>
        {notifications.length > 0 ? (
          notifications.map((notif) => (
            <NotificationItem
              key={notif.id}
              notification={notif}
              onClick={onNotificationClick}
            />
          ))
        ) : (
          <div className={styles.emptyState}>No new notifications</div>
        )}
      </div>

      {onViewAllActivity && (
        <div className={styles.footer}>
          <button
            type="button"
            onClick={onViewAllActivity}
            className={styles.viewAllLink}
            data-testid="view-all-activity-btn"
          >
            View all activity
          </button>
        </div>
      )}
    </div>
  );
};
