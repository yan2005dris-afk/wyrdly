import { useCallback, useMemo, useState, type FC } from "react";
import { Outlet, useNavigate, useLocation } from "react-router-dom";
import type { MainLayoutProps } from "./MainLayout.types";
import type { UserProfileSummary } from "../../../types/domain";
import {
  NotificationPopover,
  PushPermissionBanner,
  useNotifications,
  useWebPush,
  type SocialNotification,
} from "../../../features/notifications";
import type { NotificationDto } from "../../../features/notifications/types";
import { useAuth } from "../../../features/auth";
import { useUserProfile } from "../../../features/profile";
import { AppNavbar } from "../AppNavbar";
import { SidebarNav } from "../SidebarNav";
import { UserSummaryCard } from "../../../features/social";
import styles from "./MainLayout.module.css";

/** Maps the backend {@link NotificationDto} projection into the shape the
 *  {@link NotificationItem} component expects. Best-effort: missing actor
 *  fields fall back to placeholders so the component never receives
 *  `undefined` where a string is required. */
const toSocialNotification = (dto: NotificationDto): SocialNotification => {
  const actor: UserProfileSummary = {
    id: dto.actor.id,
    username: dto.actor.username ?? "",
    fullName: dto.actor.fullName ?? "Someone",
    avatarUrl: dto.actor.avatarUrl,
    instanceUrl: dto.actor.instanceUrl ?? "",
    isVerified: false,
    stats: { postsCount: 0, followersCount: 0, followingCount: 0 },
  };
  return {
    id: dto.id,
    type: dto.type,
    actor,
    message: dto.body,
    targetResourceId: dto.targetResourceId,
    createdAt: dto.createdAt,
    isRead: dto.isRead,
  };
};

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

  const location = useLocation();
  const searchParamQ =
    location.pathname === "/explore"
      ? (new URLSearchParams(location.search).get("q") ?? "")
      : "";

  const [lastSyncedQ, setLastSyncedQ] = useState(searchParamQ);
  const [searchQuery, setSearchQuery] = useState(searchParamQ);

  if (lastSyncedQ !== searchParamQ) {
    setLastSyncedQ(searchParamQ);
    setSearchQuery(searchParamQ);
  }

  const [isNotificationsOpen, setIsNotificationsOpen] = useState(false);
  const [pushBannerDismissed, setPushBannerDismissed] = useState(false);
  const [isSubscribing, setIsSubscribing] = useState(false);

  const { profile: apiProfile } = useUserProfile(user?.username);

  const { isSupported, permission, subscribe } = useWebPush({
    enabled: Boolean(user),
  });
  const showPushBanner =
    !pushBannerDismissed && isSupported && permission === "default";

  const handleEnablePush = async () => {
    setIsSubscribing(true);
    try {
      await subscribe();
    } finally {
      setIsSubscribing(false);
    }
  };

  const {
    notifications: notificationDtos,
    unreadCount,
    markRead: markReadApi,
    markAllRead: markAllReadApi,
  } = useNotifications({ enabled: Boolean(user) });

  const notifications = useMemo(
    () => notificationDtos.map(toSocialNotification),
    [notificationDtos],
  );

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

  const unreadAlertsCount = unreadCount;

  const handleSearchSubmit = (query: string) => {
    const trimmed = query.trim();
    if (trimmed) {
      navigate(`/explore?q=${encodeURIComponent(trimmed)}`);
    } else {
      navigate("/explore");
    }
  };

  const handleMarkAllRead = useCallback(() => {
    void markAllReadApi();
  }, [markAllReadApi]);

  // Allow the popover to mark a single item read; currently the popover only
  // exposes "mark all", but the hook API is here for future item-level clicks.
  void markReadApi;

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
        onSearchSubmit={handleSearchSubmit}
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
          {showPushBanner ? (
            <PushPermissionBanner
              onEnable={handleEnablePush}
              onDismiss={() => setPushBannerDismissed(true)}
              isLoading={isSubscribing}
              className={styles.pushBanner}
            />
          ) : null}
        </aside>

        {/* Dynamic Route Content */}
        <main className={styles.mainContent} data-testid="main-layout-content">
          <Outlet context={{ currentUser: currentUserSummary }} />
        </main>
      </div>
    </div>
  );
};
