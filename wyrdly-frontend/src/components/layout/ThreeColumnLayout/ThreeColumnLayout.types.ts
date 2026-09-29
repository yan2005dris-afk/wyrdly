import type { ReactNode } from "react";

export interface ThreeColumnLayoutProps {
  readonly navbar?: ReactNode;
  readonly banner?: ReactNode;
  readonly leftSidebar?: ReactNode;
  readonly children: ReactNode;
  readonly rightSidebar?: ReactNode;
  readonly className?: string;
  readonly mainClassName?: string;
}
