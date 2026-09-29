import type { ReactNode } from "react";

export interface TabItem<T extends string = string> {
  readonly id: T;
  readonly label: string;
  readonly count?: number;
  readonly icon?: ReactNode;
  readonly disabled?: boolean;
}

export type TabsVariant = "pill" | "underline";

export interface TabsProps<T extends string = string> {
  readonly items: readonly TabItem<T>[];
  readonly activeTab: T;
  readonly onChange: (tabId: T) => void;
  readonly variant?: TabsVariant;
  readonly className?: string;
  readonly ariaLabel?: string;
}
