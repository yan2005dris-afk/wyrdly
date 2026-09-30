import type { SocialNotification } from "../../types";

export interface NotificationItemProps {
  readonly notification: SocialNotification;
  readonly onClick?: (notificationId: string) => void;
  readonly className?: string;
}
