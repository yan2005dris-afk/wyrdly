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
import { BottomNav } from "../BottomNav";
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

// Notifications used to be hardcoded here; they now come from useNotifications
// (real data persisted by the backend when follow/reaction listeners fire).

export const MainLayout: FC<MainLayoutProps> = ({
  className = "",
  unreadMessagesCount = 0,
}) => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const location = useLocation();
  const searchParamQ =
    location.pathname === "/explore"
      ? (new URLSearchParams(location.search).get("q") ?? "")
      : "";

  const [searchQueryOverride, setSearchQueryOverride] = useState<string | null>(
    null,
  );
  // When the user has typed into the search input, prefer that value; otherwise
  // mirror the URL's ?q= parameter so back/forward navigation stays in sync.
  const searchQuery = searchQueryOverride ?? searchParamQ;
  const setSearchQuery = (q: string) => setSearchQueryOverride(q);

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

  const unreadNotificationsCount = unreadCount;

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

  const handleNotificationClick = useCallback(
    (id: string, notification?: SocialNotification) => {
      void markReadApi(id);

      const target = notification ?? notifications.find((n) => n.id === id);
      if (target?.type === "CHAT_MESSAGE" && target.actor) {
        setIsNotificationsOpen(false);
        const params = new URLSearchParams({
          userId: target.actor.id,
          username: target.actor.username,
        });
        navigate(`/chat?${params.toString()}`);
      }
    },
    [markReadApi, notifications, navigate],
  );

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
        unreadNotificationsCount={unreadNotificationsCount}
        isNotificationsOpen={isNotificationsOpen}
        onNotificationsClick={() =>
          setIsNotificationsOpen(!isNotificationsOpen)
        }
        notificationsSlot={
          <NotificationPopover
            notifications={notifications}
            onMarkAllAsRead={handleMarkAllRead}
            onNotificationClick={handleNotificationClick}
            onViewAllActivity={() => {
              setIsNotificationsOpen(false);
              navigate("/notifications");
            }}
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
            unreadMessagesCount={unreadMessagesCount}
            unreadNotificationsCount={unreadNotificationsCount}
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

      {/* Persistent Bottom Navigation for Mobile (< 1024px) */}
      <BottomNav
        unreadMessagesCount={unreadMessagesCount}
        profileUsername={user?.username}
        onNewPostClick={() => {
          navigate("/feed");
          window.scrollTo({ top: 0, behavior: "smooth" });
        }}
      />
    </div>
  );
};
