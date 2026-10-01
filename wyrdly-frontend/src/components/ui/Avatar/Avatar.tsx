import { useState, type FC } from "react";
import type { AvatarProps, AvatarSize, AvatarShape } from "./Avatar.types";
import { AuthImage } from "../AuthImage";
import { defaultAvatarDataUrl } from "../../../utils/defaultAvatar";
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

  // Inline SVG avatar used both as the AuthImage fallback (so failed
  // loads switch seamlessly to the SVG) and as the rendered element
  // when src is missing or has previously failed.
  const inlineAvatar = defaultAvatarDataUrl(alt, fallbackInitials ?? alt);

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
        <AuthImage
          src={src}
          alt={alt}
          className={`${styles.avatarImage} ${SHAPE_CLASSES[shape]}`}
          fallbackSrc={inlineAvatar}
          onError={() => setHasError(true)}
        />
      ) : (
        <img
          src={inlineAvatar}
          alt={alt}
          className={`${styles.avatarImage} ${SHAPE_CLASSES[shape]}`}
          data-testid="avatar-fallback"
        />
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
