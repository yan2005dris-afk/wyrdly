import { useState, type FC } from "react";
import { Outlet, useNavigate } from "react-router-dom";
import type { MainLayoutProps } from "./MainLayout.types";
import type { UserProfileSummary } from "../../../types/domain";
import type { SocialNotification } from "../../../types/notifications";
import { useAuth } from "../../../hooks/useAuth";
import { useUserProfile } from "../../../hooks/useUserProfile";
import { AppNavbar } from "../AppNavbar";
import { SidebarNav } from "../SidebarNav";
import { UserSummaryCard } from "../../social/UserSummaryCard";
import { NotificationPopover } from "../../notifications/NotificationPopover";
import styles from "./MainLayout.module.css";

const INITIAL_NOTIFICATIONS: readonly SocialNotification[] = [
  {
    id: "notif-1",
    type: "POST_BOOST",
    actor: {
      id: "user-alice",
      username: "alice",
      fullName: "Alice Chen",
      avatarUrl:
        "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=100&auto=format&fit=crop&q=80",
      isVerified: true,
      instanceUrl: "wyrdly.app",
      stats: { postsCount: 45, followersCount: 120, followingCount: 80 },
    },
    message: "boosted your relay announcement post",
    createdAt: "10m ago",
    isRead: false,
  },
  {
    id: "notif-2",
    type: "GRAPH_FOLLOW",
    actor: {
      id: "user-jonas",
      username: "jonas",
      fullName: "Jonas Weber",
      avatarUrl:
        "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=100&auto=format&fit=crop&q=80",
      isVerified: true,
      instanceUrl: "mastodon.social",
      stats: { postsCount: 180, followersCount: 4200, followingCount: 650 },
    },
    message: "started following your activity graph",
    createdAt: "1h ago",
    isRead: false,
  },
];

export const MainLayout: FC<MainLayoutProps> = ({ className = "" }) => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const [searchQuery, setSearchQuery] = useState("");
  const [isNotificationsOpen, setIsNotificationsOpen] = useState(false);
  const [notifications, setNotifications] = useState<
    readonly SocialNotification[]
  >(INITIAL_NOTIFICATIONS);

  const { profile: apiProfile } = useUserProfile(user?.username);

  const currentUserSummary: UserProfileSummary = apiProfile ?? {
    id: user?.id || "usr-current",
    username: user?.username || "user",
    fullName: user?.fullName || "User",
    avatarUrl: user?.avatarUrl,
    bio: user?.bio,
    instanceUrl: "wyrdly.app",
    isVerified: false,
    stats: {
      postsCount: 0,
      followersCount: 0,
      followingCount: 0,
    },
  };

  const unreadAlertsCount = notifications.filter((n) => !n.isRead).length;

  const handleMarkAllRead = () => {
    setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })));
  };

  return (
    <div
      className={`${styles.pageContainer} ${className}`}
      data-testid="main-layout"
    >
      {/* Global Navigation Header */}
      <AppNavbar
        currentUser={currentUserSummary}
        searchQuery={searchQuery}
        onSearchChange={setSearchQuery}
        statusVariant="federated"
        statusText="Federated • Live"
        unreadNotificationsCount={unreadAlertsCount}
        isNotificationsOpen={isNotificationsOpen}
        onNotificationsClick={() =>
          setIsNotificationsOpen(!isNotificationsOpen)
        }
        notificationsSlot={
          <NotificationPopover
            notifications={notifications}
            onMarkAllAsRead={handleMarkAllRead}
          />
        }
        onLogout={logout}
      />

      {/* Grid Container with Persistent Sidebar & Outlet */}
      <div className={styles.gridContainer}>
        {/* Left Sidebar (Sticky on Desktop) */}
        <aside className={styles.leftSidebar} data-testid="main-layout-sidebar">
          <UserSummaryCard user={currentUserSummary} />
          <SidebarNav
            unreadMessagesCount={3}
            unreadAlertsCount={unreadAlertsCount}
            onLogout={logout}
            onNewPostClick={() => {
              navigate("/feed");
              window.scrollTo({ top: 0, behavior: "smooth" });
            }}
          />
        </aside>

        {/* Dynamic Route Content */}
        <main className={styles.mainContent} data-testid="main-layout-content">
          <Outlet context={{ currentUser: currentUserSummary }} />
        </main>
      </div>
    </div>
  );
};
