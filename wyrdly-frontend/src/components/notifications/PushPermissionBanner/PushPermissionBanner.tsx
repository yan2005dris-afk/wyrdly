import type { FC } from "react";
import { Bell } from "lucide-react";
import type { PushPermissionBannerProps } from "./PushPermissionBanner.types";
import styles from "./PushPermissionBanner.module.css";

export const PushPermissionBanner: FC<PushPermissionBannerProps> = ({
  onEnable,
  onDismiss,
  isLoading = false,
  className = "",
}) => {
  return (
    <div
      role="banner"
      className={`${styles.banner} ${className}`}
      data-testid="push-permission-banner"
    >
      <div className={styles.leftGroup}>
        <span className={styles.bellCircle}>
          <Bell className="w-3 h-3" />
        </span>
        <p className={styles.message}>
          <strong className={styles.strongText}>
            Enable Desktop Push Notifications
          </strong>{" "}
          to get instant alerts when your network posts.
        </p>
      </div>

      <div className={styles.actionsGroup}>
        <button
          type="button"
          onClick={onEnable}
          disabled={isLoading}
          className={styles.enableButton}
          data-testid="enable-push-btn"
        >
          {isLoading ? "Enabling..." : "Enable"}
        </button>
        <button
          type="button"
          onClick={onDismiss}
          className={styles.dismissButton}
          data-testid="dismiss-push-btn"
        >
          Not now
        </button>
      </div>
    </div>
  );
};
