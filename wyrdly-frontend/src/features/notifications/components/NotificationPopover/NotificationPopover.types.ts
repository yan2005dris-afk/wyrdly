import type { SocialNotification } from "../../types";

export interface NotificationPopoverProps {
  readonly notifications: readonly SocialNotification[];
  readonly onMarkAllAsRead?: () => void;
  readonly onNotificationClick?: (
    notificationId: string,
    notification?: SocialNotification,
  ) => void;
  readonly onViewAllActivity?: () => void;
  readonly className?: string;
}
