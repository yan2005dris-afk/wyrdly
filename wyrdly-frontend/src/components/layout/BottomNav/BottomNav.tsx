import type { FC } from "react";
import { NavLink } from "react-router-dom";
import { Home, Compass, MessageSquare, User, Plus } from "lucide-react";
import type { BottomNavProps } from "./BottomNav.types";
import styles from "./BottomNav.module.css";

export const BottomNav: FC<BottomNavProps> = ({
  unreadMessagesCount = 0,
  onNewPostClick,
  profileUsername,
  className = "",
}) => {
  const profilePath = profileUsername
    ? `/profile/${encodeURIComponent(profileUsername)}`
    : "/profile";

  return (
    <nav
      className={`${styles.bottomNav} ${className}`}
      data-testid="bottom-nav"
      aria-label="Mobile Navigation"
    >
      <NavLink
        to="/feed"
        className={({ isActive }) =>
          `${styles.navItem} ${isActive ? styles.navItemActive : ""}`
        }
        data-testid="bottom-nav-feed"
      >
        <Home className="w-5 h-5" />
        <span>Feed</span>
      </NavLink>

      <NavLink
        to="/explore"
        className={({ isActive }) =>
          `${styles.navItem} ${isActive ? styles.navItemActive : ""}`
        }
        data-testid="bottom-nav-explore"
      >
        <Compass className="w-5 h-5" />
        <span>Explore</span>
      </NavLink>

      {onNewPostClick && (
        <button
          type="button"
          onClick={onNewPostClick}
          className={styles.newPostButton}
          aria-label="New post"
          data-testid="bottom-nav-new-post"
        >
          <Plus className="w-5 h-5" />
        </button>
      )}

      <NavLink
        to="/chat"
        className={({ isActive }) =>
          `${styles.navItem} ${isActive ? styles.navItemActive : ""}`
        }
        data-testid="bottom-nav-chat"
      >
        <div className={styles.iconWrapper}>
          <MessageSquare className="w-5 h-5" />
          {unreadMessagesCount > 0 && (
            <span
              className={styles.badge}
              data-testid="bottom-nav-chat-badge"
              aria-label={`${unreadMessagesCount} unread messages`}
            >
              {unreadMessagesCount > 99 ? "99+" : unreadMessagesCount}
            </span>
          )}
        </div>
        <span>Chat</span>
      </NavLink>

      <NavLink
        to={profilePath}
        className={({ isActive }) =>
          `${styles.navItem} ${isActive ? styles.navItemActive : ""}`
        }
        data-testid="bottom-nav-profile"
      >
        <User className="w-5 h-5" />
        <span>Profile</span>
      </NavLink>
    </nav>
  );
};
