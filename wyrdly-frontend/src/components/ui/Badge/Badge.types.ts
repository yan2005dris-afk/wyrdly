import type { ReactNode } from "react";

export type BadgeVariant =
  | "federated"
  | "websocket"
  | "storage"
  | "counter"
  | "neutral"
  | "success"
  | "danger";

export type BadgeSize = "sm" | "md";

export interface BadgeProps {
  readonly variant?: BadgeVariant;
  readonly size?: BadgeSize;
  readonly ping?: boolean;
  readonly icon?: ReactNode;
  readonly children: ReactNode;
  readonly className?: string;
}
