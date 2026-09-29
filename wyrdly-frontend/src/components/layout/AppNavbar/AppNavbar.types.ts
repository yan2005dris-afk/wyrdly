import type { ReactNode } from "react";
import type { UserProfileSummary } from "../../../types/domain";

export type NavbarStatusVariant = "federated" | "websocket" | "none";

export interface AppNavbarProps {
  readonly currentUser?: UserProfileSummary;
  readonly showSearch?: boolean;
  readonly searchQuery?: string;
  readonly onSearchChange?: (query: string) => void;
  readonly onSearchSubmit?: (query: string) => void;
  readonly statusVariant?: NavbarStatusVariant;
  readonly statusText?: string;
  readonly unreadNotificationsCount?: number;
  readonly onNotificationsClick?: () => void;
  readonly isNotificationsOpen?: boolean;
  readonly notificationsSlot?: ReactNode;
  readonly backTo?: string;
  readonly backLabel?: string;
  readonly actions?: ReactNode;
  readonly onLogout?: () => void;
  readonly className?: string;
}
