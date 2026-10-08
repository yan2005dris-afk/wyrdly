import type { SocialNotification } from "../../types";

export interface NotificationItemProps {
  readonly notification: SocialNotification;
  readonly onClick?: (
    notificationId: string,
    notification?: SocialNotification,
  ) => void;
  readonly onDoubleClick?: (notification: SocialNotification) => void;
  readonly className?: string;
}
