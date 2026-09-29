import { useState, type FC } from "react";
import type { AvatarProps, AvatarSize, AvatarShape } from "./Avatar.types";
import styles from "./Avatar.module.css";

const SIZE_CLASSES: Record<AvatarSize, string> = {
  xs: styles.sizeXs,
  sm: styles.sizeSm,
  md: styles.sizeMd,
  lg: styles.sizeLg,
  xl: styles.sizeXl,
};

const DOT_SIZE_CLASSES: Record<AvatarSize, string> = {
  xs: styles.onlineXs,
  sm: styles.onlineSm,
  md: styles.onlineMd,
  lg: styles.onlineLg,
  xl: styles.onlineXl,
};

const SHAPE_CLASSES: Record<AvatarShape, string> = {
  circle: styles.circle,
  rounded: styles.rounded,
};

const RING_CLASSES: Record<NonNullable<AvatarProps["ringColor"]>, string> = {
  indigo: styles.ringIndigo,
  white: styles.ringWhite,
  slate: styles.ringSlate,
};

export const Avatar: FC<AvatarProps> = ({
  src,
  alt,
  size = "md",
  shape = "circle",
  isOnline = false,
  showRing = false,
  ringColor = "indigo",
  fallbackInitials,
  className = "",
  onClick,
}) => {
  const [hasError, setHasError] = useState(false);

  const getInitials = (text: string): string => {
    if (fallbackInitials) return fallbackInitials.slice(0, 2);
    const words = text.trim().split(/\s+/);
    if (words.length === 1) return words[0].slice(0, 2).toUpperCase();
    return (words[0][0] + words[1][0]).toUpperCase();
  };

  const ringClass = showRing ? RING_CLASSES[ringColor] : "";
  const containerClasses = [
    styles.container,
    SIZE_CLASSES[size],
    SHAPE_CLASSES[shape],
    ringClass,
    className,
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <div
      className={containerClasses}
      onClick={onClick}
      role={onClick ? "button" : undefined}
      tabIndex={onClick ? 0 : undefined}
      data-testid="avatar-container"
    >
      {src && !hasError ? (
        <img
          src={src}
          alt={alt}
          className={`${styles.avatarImage} ${SHAPE_CLASSES[shape]}`}
          onError={() => setHasError(true)}
          loading="lazy"
        />
      ) : (
        <div
          className={`${styles.fallback} ${SHAPE_CLASSES[shape]}`}
          data-testid="avatar-fallback"
        >
          {getInitials(alt)}
        </div>
      )}

      {isOnline && (
        <span
          className={`${styles.onlineBadge} ${DOT_SIZE_CLASSES[size]}`}
          data-testid="avatar-online-dot"
          aria-label="Online status"
        />
      )}
    </div>
  );
};
