import type { ReactNode } from "react";

export type NavItemId = "feed" | "explore" | "messages" | "profile" | "alerts";

export interface NavItemConfig {
  readonly id: NavItemId;
  readonly label: string;
  readonly path: string;
  readonly icon: ReactNode;
  readonly count?: number;
}

export interface SidebarNavProps {
  readonly currentPath?: string;
  readonly unreadMessagesCount?: number;
  readonly unreadAlertsCount?: number;
  readonly onNewPostClick?: () => void;
  readonly onLogout?: () => void;
  readonly connectedRelaysCount?: number;
  readonly pingMs?: number;
  readonly className?: string;
}
