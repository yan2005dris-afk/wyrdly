import type { SocialNotification } from "../../../types/notifications";

export interface NotificationItemProps {
  readonly notification: SocialNotification;
  readonly onClick?: (notificationId: string) => void;
  readonly className?: string;
}
