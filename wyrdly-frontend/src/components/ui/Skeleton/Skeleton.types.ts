export type SkeletonVariant = "text" | "circular" | "rectangular" | "rounded";
export type SkeletonAnimation = "pulse" | "wave" | "none";

export interface SkeletonProps {
  readonly variant?: SkeletonVariant;
  readonly animation?: SkeletonAnimation;
  readonly width?: string | number;
  readonly height?: string | number;
  readonly className?: string;
  readonly style?: React.CSSProperties;
}
