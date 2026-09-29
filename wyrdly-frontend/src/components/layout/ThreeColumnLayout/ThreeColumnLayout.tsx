import type { FC } from "react";
import type { ThreeColumnLayoutProps } from "./ThreeColumnLayout.types";
import styles from "./ThreeColumnLayout.module.css";

export const ThreeColumnLayout: FC<ThreeColumnLayoutProps> = ({
  navbar,
  banner,
  leftSidebar,
  children,
  rightSidebar,
  className = "",
  mainClassName = "",
}) => {
  return (
    <div
      className={`${styles.pageContainer} ${className}`}
      data-testid="three-column-layout"
    >
      {/* Top Banner (e.g. VAPID Web Push prompt) */}
      {banner && <div className={styles.bannerSlot}>{banner}</div>}

      {/* Sticky Header / Navbar */}
      {navbar}

      {/* Main 12-Column Responsive Grid */}
      <div className={styles.gridContainer}>
        {/* Left Column (3 cols) */}
        {leftSidebar && (
          <aside
            className={styles.leftColumn}
            data-testid="layout-left-sidebar"
          >
            {leftSidebar}
          </aside>
        )}

        {/* Center Column (6 cols or 9 cols if no right sidebar) */}
        <main
          className={`${styles.centerColumn} ${
            !rightSidebar ? styles.centerExpanded : ""
          } ${mainClassName}`}
          data-testid="layout-center-main"
        >
          {children}
        </main>

        {/* Right Column (3 cols) */}
        {rightSidebar && (
          <aside
            className={styles.rightColumn}
            data-testid="layout-right-sidebar"
          >
            {rightSidebar}
          </aside>
        )}
      </div>
    </div>
  );
};
