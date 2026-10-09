import { useState, useMemo, type FC } from "react";
import { useNavigate } from "react-router-dom";
import { Bell, BellOff, CheckCheck, AlertCircle } from "lucide-react";
import {
  useNotifications,
  NotificationItem,
  type SocialNotification,
  type NotificationDto,
} from "../features/notifications";
import { GraphSuggestionsCard, useGraphSuggestions } from "../features/social";
import { Button } from "../components/ui/Button";
import { Tabs, type TabItem } from "../components/ui/Tabs";
import { PostDetailModal } from "../components/common/PostDetailModal";

type NotificationFilter = "all" | "unread";

function isSocialNotification(
  item: NotificationDto | SocialNotification,
): item is SocialNotification {
  return "message" in item;
}

function normalizeNotification(
  item: NotificationDto | SocialNotification,
): SocialNotification {
  if (isSocialNotification(item)) {
    return item;
  }

  return {
    id: item.id,
    type: item.type,
    actor: {
      id: item.actor.id,
      username: item.actor.username,
      fullName: item.actor.fullName,
      avatarUrl: item.actor.avatarUrl,
      instanceUrl: item.actor.instanceUrl ?? "wyrdly.social",
      bio: "",
      isVerified: false,
      stats: {
        postsCount: 0,
        followersCount: 0,
        followingCount: 0,
      },
    },
    message: item.body || item.title || "interacted with your content",
    targetResourceId: item.targetResourceId,
    targetSnippet: undefined,
    createdAt: item.createdAt,
    isRead: item.isRead,
  };
}

export const NotificationsPage: FC = () => {
  const navigate = useNavigate();
  const [activeFilter, setActiveFilter] = useState<NotificationFilter>("all");
  const [selectedPostId, setSelectedPostId] = useState<string | null>(null);
  const [isPostModalOpen, setIsPostModalOpen] = useState(false);

  const {
    notifications,
    unreadCount,
    isLoading,
    error,
    refetch,
    markRead,
    markAllRead,
  } = useNotifications();

  const handleNotificationInteract = (notification: SocialNotification) => {
    if (!notification.isRead) {
      void markRead(notification.id);
    }

    if (notification.type === "CHAT_MESSAGE" && notification.actor) {
      const params = new URLSearchParams({
        userId: notification.actor.id,
        username: notification.actor.username,
      });
      navigate(`/chat?${params.toString()}`);
      return;
    }

    if (notification.type !== "CHAT_MESSAGE" && notification.targetResourceId) {
      setSelectedPostId(notification.targetResourceId);
      setIsPostModalOpen(true);
    }
  };

  const handleClosePostModal = () => {
    setIsPostModalOpen(false);
    setSelectedPostId(null);
  };

  const {
    suggestions,
    isLoading: isSuggestionsLoading,
    refetch: refetchSuggestions,
  } = useGraphSuggestions();

  const normalizedNotifications = useMemo(() => {
    return (notifications as (NotificationDto | SocialNotification)[]).map(
      normalizeNotification,
    );
  }, [notifications]);

  const filteredNotifications = useMemo(() => {
    if (activeFilter === "unread") {
      return normalizedNotifications.filter((n) => !n.isRead);
    }
    return normalizedNotifications;
  }, [normalizedNotifications, activeFilter]);

  const filterTabs: readonly TabItem<NotificationFilter>[] = [
    {
      id: "all",
      label: "All",
      count: normalizedNotifications.length,
    },
    {
      id: "unread",
      label: "Unread",
      count: unreadCount,
    },
  ];

  return (
    <div
      className="grid grid-cols-1 lg:grid-cols-12 gap-6 w-full"
      data-testid="notifications-page"
    >
      {/* Main Notifications Column (8 columns) */}
      <section className="lg:col-span-8 flex flex-col gap-4">
        {/* Header and Controls Card */}
        <div className="bg-white rounded-xl border border-slate-200/80 p-5 shadow-xs flex flex-col gap-4">
          <div className="flex items-center justify-between flex-wrap gap-3">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-full bg-indigo-50 text-indigo-600 flex items-center justify-center shrink-0">
                <Bell className="w-5 h-5" />
              </div>
              <div>
                <h1 className="text-xl font-bold text-slate-900 tracking-tight flex items-center gap-2">
                  Notifications
                  {unreadCount > 0 && (
                    <span
                      className="text-xs font-semibold px-2 py-0.5 rounded-full bg-rose-100 text-rose-700"
                      data-testid="unread-count-badge"
                    >
                      {unreadCount} unread
                    </span>
                  )}
                </h1>
                <p className="text-xs text-slate-500 mt-0.5">
                  Stay updated with your federated social interactions
                </p>
              </div>
            </div>

            <Button
              variant="outline"
              size="sm"
              onClick={() => void markAllRead()}
              disabled={unreadCount === 0 || isLoading}
              leftIcon={<CheckCheck className="w-4 h-4" />}
              data-testid="mark-all-read-btn"
            >
              Mark all read
            </Button>
          </div>

          {/* Filter Tabs */}
          <div className="border-t border-slate-100 pt-3">
            <Tabs
              items={filterTabs}
              activeTab={activeFilter}
              onChange={setActiveFilter}
              variant="pill"
            />
          </div>
        </div>

        {/* Error State */}
        {error && (
          <div
            className="bg-rose-50 border border-rose-200 rounded-xl p-4 text-rose-800 text-sm flex items-center justify-between"
            role="alert"
            data-testid="notifications-error"
          >
            <div className="flex items-center gap-2">
              <AlertCircle className="w-4 h-4 shrink-0 text-rose-600" />
              <span>Failed to load notifications: {error.message}</span>
            </div>
            <Button
              variant="outline"
              size="sm"
              onClick={() => void refetch()}
              data-testid="retry-btn"
            >
              Try again
            </Button>
          </div>
        )}

        {/* Loading State */}
        {isLoading && (
          <div
            className="bg-white rounded-xl border border-slate-200/80 p-4 flex flex-col gap-4 shadow-xs"
            data-testid="notifications-loading"
          >
            {Array.from({ length: 4 }).map((_, i) => (
              <div
                key={i}
                className="flex items-center gap-3 animate-pulse p-2"
                data-testid="notification-skeleton"
              >
                <div className="w-9 h-9 rounded-full bg-slate-200 shrink-0" />
                <div className="flex-1 space-y-2">
                  <div className="h-3.5 bg-slate-200 rounded w-3/4" />
                  <div className="h-2.5 bg-slate-100 rounded w-1/4" />
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Empty State */}
        {!isLoading && !error && filteredNotifications.length === 0 && (
          <div
            className="bg-white rounded-xl border border-slate-200/80 p-12 flex flex-col items-center justify-center text-center shadow-xs"
            data-testid="notifications-empty"
          >
            <div className="w-12 h-12 rounded-full bg-slate-100 text-slate-400 flex items-center justify-center mb-3">
              <BellOff className="w-6 h-6" />
            </div>
            <h3 className="text-base font-semibold text-slate-800">
              {activeFilter === "unread"
                ? "No unread notifications"
                : "No notifications yet"}
            </h3>
            <p className="text-sm text-slate-500 max-w-sm mt-1">
              {activeFilter === "unread"
                ? "You're all caught up! Switch to 'All' to review previous activity."
                : "When other users interact with your posts or follow you, your notifications will show up here."}
            </p>
          </div>
        )}

        {/* Notifications List */}
        {!isLoading && !error && filteredNotifications.length > 0 && (
          <div
            className="bg-white rounded-xl border border-slate-200/80 overflow-hidden divide-y divide-slate-100 shadow-xs"
            data-testid="notifications-list"
          >
            {filteredNotifications.map((notif) => (
              <NotificationItem
                key={notif.id}
                notification={notif}
                onClick={(_id, n) => handleNotificationInteract(n ?? notif)}
                onDoubleClick={(n) => handleNotificationInteract(n)}
              />
            ))}
          </div>
        )}
      </section>

      {/* Right Social Suggestions Column (4 columns) */}
      <aside className="lg:col-span-4 flex flex-col gap-4">
        <GraphSuggestionsCard
          suggestions={suggestions}
          isLoading={isSuggestionsLoading}
          onAfterToggle={() => {
            void refetchSuggestions();
          }}
        />
      </aside>

      {/* Post Detail Modal */}
      <PostDetailModal
        postId={selectedPostId}
        isOpen={isPostModalOpen}
        onClose={handleClosePostModal}
      />
    </div>
  );
};
