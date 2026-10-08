import { useEffect, useRef, useState, type FC, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { Share2, Search, Bell, ArrowLeft, ChevronDown } from "lucide-react";
import type { AppNavbarProps } from "./AppNavbar.types";
import { Avatar } from "../../ui/Avatar";
import { Badge } from "../../ui/Badge";
import { Input } from "../../ui/Input";
import { IconButton } from "../../ui/Button";
import styles from "./AppNavbar.module.css";

export const AppNavbar: FC<AppNavbarProps> = ({
  currentUser,
  showSearch = true,
  searchQuery = "",
  onSearchChange,
  onSearchSubmit,
  statusVariant = "federated",
  statusText,
  unreadNotificationsCount = 0,
  onNotificationsClick,
  isNotificationsOpen = false,
  notificationsSlot,
  backTo,
  backLabel = "Back",
  actions,
  onLogout,
  className = "",
}) => {
  const [isProfileMenuOpen, setIsProfileMenuOpen] = useState(false);
  const profileMenuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!isProfileMenuOpen) {
      return;
    }

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setIsProfileMenuOpen(false);
      }
    };

    const handleMouseDown = (event: MouseEvent) => {
      if (
        profileMenuRef.current &&
        !profileMenuRef.current.contains(event.target as Node)
      ) {
        setIsProfileMenuOpen(false);
      }
    };

    document.addEventListener("keydown", handleKeyDown);
    document.addEventListener("mousedown", handleMouseDown);

    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      document.removeEventListener("mousedown", handleMouseDown);
    };
  }, [isProfileMenuOpen]);

  const closeProfileMenu = () => {
    setIsProfileMenuOpen(false);
  };

  const handleLogoutClick = () => {
    closeProfileMenu();
    onLogout?.();
  };

  const handleSearchSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (onSearchSubmit) {
      onSearchSubmit(searchQuery);
    }
  };

  const getStatusBadge = () => {
    if (statusVariant === "federated") {
      return (
        <Badge variant="federated" ping={true}>
          {statusText || "Federated • Live"}
        </Badge>
      );
    }
    if (statusVariant === "websocket") {
      return (
        <Badge variant="websocket" ping={true}>
          {statusText || "WebSocket • Connected"}
        </Badge>
      );
    }
    return null;
  };

  return (
    <header
      className={`${styles.header} ${className}`}
      data-testid="app-navbar"
    >
      <div className={styles.inner}>
        {/* Left: Brand or Back Button */}
        <div className={styles.leftSection}>
          {backTo ? (
            <Link
              to={backTo}
              className={styles.backButton}
              data-testid="navbar-back-link"
            >
              <ArrowLeft className="w-4 h-4" />
              <span>{backLabel}</span>
            </Link>
          ) : (
            <Link
              to="/feed"
              className={styles.brandLink}
              data-testid="navbar-brand-link"
            >
              <div className={styles.brandIcon}>
                <Share2 className="w-5 h-5" />
              </div>
              <div>
                <span className={styles.brandText}>Wyrdly</span>
                <span className={styles.brandSubtext}>
                  Where connections weave the future
                </span>
              </div>
            </Link>
          )}
        </div>

        {/* Center: Search Omnibox */}
        {showSearch && (
          <div className={styles.centerSection}>
            <form onSubmit={handleSearchSubmit}>
              <Input
                variant="pill"
                placeholder="Search people, relays, hashtags..."
                value={searchQuery}
                onChange={(e) => onSearchChange?.(e.target.value)}
                leftIcon={<Search className="w-3.5 h-3.5 text-slate-400" />}
                data-testid="navbar-search-input"
              />
            </form>
          </div>
        )}

        {/* Right: Status badge, Notification Popover trigger, Actions, Avatar */}
        <div className={styles.rightSection}>
          <div className={styles.statusContainer}>{getStatusBadge()}</div>

          {actions}

          <div className={styles.notificationsContainer}>
            <IconButton
              icon={<Bell className="w-4 h-4" />}
              ariaLabel="Notifications"
              variant={isNotificationsOpen ? "secondary" : "ghost"}
              badgeCount={unreadNotificationsCount}
              onClick={onNotificationsClick}
              data-testid="navbar-notifications-btn"
            />

            {isNotificationsOpen && notificationsSlot && (
              <div
                className={styles.popoverDropdown}
                data-testid="navbar-notifications-popover"
              >
                {notificationsSlot}
              </div>
            )}
          </div>

          <div className={styles.profileMenuContainer} ref={profileMenuRef}>
            <Link
              to={currentUser ? `/profile/${currentUser.username}` : "/login"}
              data-testid="navbar-profile-link"
            >
              <Avatar
                src={currentUser?.avatarUrl}
                alt={currentUser?.fullName || currentUser?.username || "Guest"}
                size="sm"
                showRing={true}
                ringColor="indigo"
              />
            </Link>

            {onLogout && currentUser && (
              <>
                <IconButton
                  icon={<ChevronDown className="w-4 h-4" />}
                  ariaLabel="Open account menu"
                  variant="ghost"
                  size="sm"
                  aria-haspopup="menu"
                  aria-expanded={isProfileMenuOpen}
                  data-testid="navbar-profile-menu-btn"
                  onClick={() => setIsProfileMenuOpen((open) => !open)}
                />

                {isProfileMenuOpen && (
                  <div
                    className={styles.profileMenuDropdown}
                    role="menu"
                    data-testid="navbar-profile-menu"
                  >
                    <Link
                      to={`/profile/${currentUser.username}`}
                      role="menuitem"
                      className={styles.menuItem}
                      data-testid="navbar-profile-link-menu"
                      onClick={closeProfileMenu}
                    >
                      View profile
                    </Link>
                    <button
                      type="button"
                      role="menuitem"
                      className={`${styles.menuItem} ${styles.logoutItem}`}
                      data-testid="navbar-logout-btn"
                      onClick={handleLogoutClick}
                    >
                      Log out
                    </button>
                  </div>
                )}
              </>
            )}
          </div>
        </div>
      </div>
    </header>
  );
};
