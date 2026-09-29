import type { SocialNotification } from "../../../types/notifications";

export interface NotificationPopoverProps {
  readonly notifications: readonly SocialNotification[];
  readonly onMarkAllAsRead?: () => void;
  readonly onNotificationClick?: (notificationId: string) => void;
  readonly onViewAllActivity?: () => void;
  readonly className?: string;
}
