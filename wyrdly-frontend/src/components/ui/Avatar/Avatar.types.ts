export type AvatarSize = "xs" | "sm" | "md" | "lg" | "xl";
export type AvatarShape = "circle" | "rounded";

export interface AvatarProps {
  readonly src?: string;
  readonly alt: string;
  readonly size?: AvatarSize;
  readonly shape?: AvatarShape;
  readonly isOnline?: boolean;
  readonly showRing?: boolean;
  readonly ringColor?: "indigo" | "white" | "slate";
  readonly fallbackInitials?: string;
  readonly className?: string;
  readonly onClick?: () => void;
}
