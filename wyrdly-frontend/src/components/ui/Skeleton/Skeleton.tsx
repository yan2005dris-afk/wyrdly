import type { FC, CSSProperties } from "react";
import type {
  SkeletonProps,
  SkeletonVariant,
  SkeletonAnimation,
} from "./Skeleton.types";
import styles from "./Skeleton.module.css";

const VARIANT_CLASSES: Record<SkeletonVariant, string> = {
  text: styles.text,
  circular: styles.circular,
  rectangular: styles.rectangular,
  rounded: styles.rounded,
};

const ANIMATION_CLASSES: Record<SkeletonAnimation, string> = {
  pulse: styles.pulse,
  wave: styles.wave,
  none: styles.none,
};

export const Skeleton: FC<SkeletonProps> = ({
  variant = "rounded",
  animation = "pulse",
  width,
  height,
  className = "",
  style,
}) => {
  const inlineStyle: CSSProperties = {
    ...style,
    width: typeof width === "number" ? `${width}px` : width,
    height: typeof height === "number" ? `${height}px` : height,
  };

  const skeletonClasses = [
    styles.skeleton,
    VARIANT_CLASSES[variant],
    ANIMATION_CLASSES[animation],
    className,
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <span
      className={skeletonClasses}
      style={inlineStyle}
      data-testid="skeleton-element"
      aria-hidden="true"
    />
  );
};
