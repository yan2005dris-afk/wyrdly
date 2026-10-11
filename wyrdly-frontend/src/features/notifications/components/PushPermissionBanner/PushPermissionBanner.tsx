import type { FC } from "react";
import { Bell } from "lucide-react";
import { pushCopy } from "../../copy";
import type { PushPermissionBannerProps } from "./PushPermissionBanner.types";
import styles from "./PushPermissionBanner.module.css";

const REASON_COPY: Record<
  NonNullable<PushPermissionBannerProps["reason"]>,
  { title: string; body: string; enableLabel: string }
> = {
  unsupported: {
    title: "Push notifications unavailable",
    body: pushCopy.unsupported,
    enableLabel: "Try again",
  },
  denied: {
    title: "Notifications blocked in this browser",
    body: pushCopy.denied,
    enableLabel: "How to enable",
  },
  "insecure-context": {
    title: "Push requires HTTPS or localhost",
    body: pushCopy.insecureContext,
    enableLabel: "Got it",
  },
  "missing-cache": {
    title: "Cache API required",
    body: pushCopy.missingCache,
    enableLabel: "Got it",
  },
};

export const PushPermissionBanner: FC<PushPermissionBannerProps> = ({
  onEnable,
  onDismiss,
  isLoading = false,
  className = "",
  reason,
}) => {
  const variant = reason ? REASON_COPY[reason] : null;
  const title = variant?.title ?? "Enable Desktop Push Notifications";
  const body = variant
    ? variant.body
    : "to get instant alerts when your network posts.";
  const enableLabel =
    variant?.enableLabel ?? (isLoading ? "Enabling..." : "Enable");

  return (
    <header
      className={`${styles.banner} ${className}`}
      data-testid="push-permission-banner"
      data-reason={reason ?? "default"}
    >
      <div className={styles.leftGroup}>
        <span className={styles.bellCircle}>
          <Bell className="w-3 h-3" />
        </span>
        <p className={styles.message}>
          <strong className={styles.strongText}>{title}</strong> {body}
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
          {enableLabel}
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
    </header>
  );
};
