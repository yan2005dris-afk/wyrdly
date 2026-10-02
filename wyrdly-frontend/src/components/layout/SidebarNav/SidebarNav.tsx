import type { FC } from "react";
import { NavLink } from "react-router-dom";
import { Home, Compass, MessageSquare, Bell, Plus, LogOut } from "lucide-react";
import type { SidebarNavProps, NavItemConfig } from "./SidebarNav.types";
import { Button } from "../../ui/Button";
import styles from "./SidebarNav.module.css";

export const SidebarNav: FC<SidebarNavProps> = ({
  unreadMessagesCount = 0,
  unreadAlertsCount = 0,
  onNewPostClick,
  onLogout,
  connectedRelaysCount = 3,
  pingMs = 42,
  className = "",
}) => {
  const navItems: NavItemConfig[] = [
    {
      id: "feed",
      label: "Feed",
      path: "/feed",
      icon: <Home className="w-4 h-4" />,
    },
    {
      id: "explore",
      label: "Explore",
      path: "/explore",
      icon: <Compass className="w-4 h-4" />,
    },
    {
      id: "messages",
      label: "Messages",
      path: "/chat",
      icon: <MessageSquare className="w-4 h-4" />,
      count: unreadMessagesCount,
    },
    {
      id: "alerts",
      label: "Alerts",
      path: "/notifications",
      icon: <Bell className="w-4 h-4" />,
      count: unreadAlertsCount,
    },
  ];

  return (
    <aside
      className={`${styles.container} ${className}`}
      data-testid="sidebar-nav"
    >
      {/* Navigation Links Card */}
      <nav className={styles.navCard}>
        {navItems.map((item) => (
          <NavLink
            key={item.id}
            to={item.path}
            className={({ isActive }) =>
              `${styles.navLink} ${isActive ? styles.navLinkActive : ""}`
            }
            data-testid={`nav-link-${item.id}`}
          >
            <div className={styles.linkContent}>
              <span className={styles.linkIcon}>{item.icon}</span>
              <span>{item.label}</span>
            </div>

            {item.count !== undefined && item.count > 0 && (
              <span
                className={`${styles.linkBadge} ${
                  item.id === "alerts" ? styles.badgeRose : styles.badgeIndigo
                }`}
                data-testid={`nav-badge-${item.id}`}
              >
                {item.count > 9 ? "9+" : item.count}
              </span>
            )}
          </NavLink>
        ))}

        {onLogout && (
          <button
            type="button"
            className={styles.logoutLink}
            data-testid="nav-logout-btn"
            onClick={onLogout}
          >
            <span className={styles.linkContent}>
              <span className={styles.linkIcon}>
                <LogOut className="w-4 h-4" />
              </span>
              <span>Log out</span>
            </span>
          </button>
        )}
      </nav>

      {/* New Post Action Button */}
      {onNewPostClick && (
        <Button
          variant="primary"
          size="lg"
          fullWidth={true}
          leftIcon={<Plus className="w-4 h-4" />}
          onClick={onNewPostClick}
          data-testid="sidebar-new-post-btn"
        >
          New Post
        </Button>
      )}

      {/* Network Node Status */}
      <div className={styles.nodeStatusCard} data-testid="sidebar-node-status">
        <span className={styles.statusLabel}>
          <span className={styles.greenDot} />
          {connectedRelaysCount} relays connected
        </span>
        <span className={styles.latencyText}>{pingMs}ms</span>
      </div>
    </aside>
  );
};
