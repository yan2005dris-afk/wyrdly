import type { FC } from "react";
import type { BadgeProps, BadgeVariant, BadgeSize } from "./Badge.types";
import styles from "./Badge.module.css";

const VARIANT_CLASSES: Record<BadgeVariant, string> = {
  federated: styles.federated,
  websocket: styles.websocket,
  storage: styles.storage,
  counter: styles.counter,
  neutral: styles.neutral,
  success: styles.success,
  danger: styles.danger,
};

const SIZE_CLASSES: Record<BadgeSize, string> = {
  sm: styles.sizeSm,
  md: styles.sizeMd,
};

export const Badge: FC<BadgeProps> = ({
  variant = "neutral",
  size = "sm",
  ping = false,
  icon,
  children,
  className = "",
}) => {
  const isCounter = variant === "counter";
  const badgeClasses = [
    styles.badge,
    VARIANT_CLASSES[variant],
    !isCounter ? SIZE_CLASSES[size] : "",
    className,
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <span className={badgeClasses} data-testid="badge-element">
      {ping && (
        <span className={styles.pingWrapper} data-testid="badge-ping">
          <span className={styles.pingAnimation} />
          <span className={styles.pingDot} />
        </span>
      )}
      {icon && <span data-testid="badge-icon">{icon}</span>}
      <span>{children}</span>
    </span>
  );
};
