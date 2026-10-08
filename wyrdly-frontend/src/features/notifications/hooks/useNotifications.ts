import { useCallback, useEffect, useMemo, useRef } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "../../../api/axios";
import type { NotificationDto, NotificationListResponseDto } from "../types";

export interface UseNotificationsOptions {
  /** When false the hook is dormant and returns an empty state. */
  readonly enabled?: boolean;
  /** Page size for the initial fetch. Defaults to 20. */
  readonly pageSize?: number;
}

export interface UseNotificationsResult {
  readonly notifications: NotificationListResponseDto["notifications"];
  readonly unreadCount: number;
  readonly isLoading: boolean;
  readonly error: Error | null;
  readonly refetch: () => Promise<void>;
  readonly markRead: (notificationId: string) => Promise<void>;
  readonly markAllRead: () => Promise<void>;
}

export const notificationsQueryKey = (pageSize: number) => [
  "notifications",
  pageSize,
];

/**
 * Manages the in-app notification feed using TanStack Query:
 * fetches the user's notifications on mount, observes incoming Web Push
 * events via BroadcastChannel/ServiceWorker with cache invalidation, and provides
 * optimistic mark-read mutations.
 */
export const useNotifications = (
  options: UseNotificationsOptions = {},
): UseNotificationsResult => {
  const { enabled = true, pageSize = 20 } = options;
  const queryClient = useQueryClient();
  const recentPushEventsRef = useRef<Map<string, number>>(new Map());
  // Stabilise the queryKey reference: notificationsQueryKey returns a fresh
  // array on every call, which would otherwise retrigger the push-listener
  // useEffect on every render and cause the BroadcastChannel to be torn down
  // and recreated — losing any incoming push that lands in that window.
  const queryKey = useMemo(
    () => notificationsQueryKey(pageSize),
    [pageSize],
  );

  const {
    data,
    isLoading,
    error,
    refetch: queryRefetch,
  } = useQuery<NotificationListResponseDto, Error>({
    queryKey,
    queryFn: async () => {
      const response = await apiClient.get<NotificationListResponseDto>(
        `/api/notifications?page=0&pageSize=${pageSize}`,
      );
      return response.data;
    },
    enabled,
    refetchOnWindowFocus: true,
  });

  const notifications = data?.notifications ?? [];
  const unreadCount = data?.unreadCount ?? 0;

  useEffect(() => {
    if (!enabled) return;

    interface PushReceivedEventData {
      title?: string;
      body?: string;
      icon?: string;
      data?: {
        url?: string;
        type?: string;
        postId?: string;
        actorId?: string;
        reactorId?: string;
        actorUsername?: string;
        actorFullName?: string;
        actorAvatarUrl?: string;
        [key: string]: unknown;
      };
    }

    const onPushReceived = (
      payload?: PushReceivedEventData,
      eventId?: string,
    ) => {
      // Event deduplication: if both BroadcastChannel and serviceWorker.onmessage
      // fire for the same push event, ignore the duplicate delivery.
      const now = Date.now();
      for (const [key, ts] of recentPushEventsRef.current.entries()) {
        if (now - ts > 5000) {
          recentPushEventsRef.current.delete(key);
        }
      }

      const dedupeKey =
        eventId ||
        [
          payload?.title,
          payload?.body,
          payload?.data?.postId,
          payload?.data?.reactorId,
          payload?.data?.followerId,
          payload?.data?.type,
        ]
          .filter(Boolean)
          .join("|") ||
        `empty_${Math.floor(now / 1000)}`;

      if (recentPushEventsRef.current.has(dedupeKey)) {
        return;
      }
      recentPushEventsRef.current.set(dedupeKey, now);

      const syntheticId =
        eventId ||
        (payload?.data?.postId
          ? `push_post_${payload.data.postId}`
          : `push_${now}`);

      // 1. Optimistic feedback: update cached unread count and prepend notification if payload is present
      queryClient.setQueryData<NotificationListResponseDto>(queryKey, (old) => {
        if (!old) return old;
        const newUnreadCount = old.unreadCount + 1;
        if (!payload || !payload.title) {
          return {
            ...old,
            unreadCount: newUnreadCount,
          };
        }

        const actorFullName =
          (payload.data?.actorFullName as string) ||
          (payload.data?.actorUsername as string) ||
          "Someone";

        const actorId =
          (payload.data?.actorId as string) ||
          (payload.data?.reactorId as string) ||
          (payload.data?.followerId as string) ||
          "usr_push";

        const syntheticNotif: NotificationDto = {
          id: syntheticId,
          type: (payload.data?.type as NotificationDto["type"]) || "POST_LIKE",
          title: payload.title,
          body: payload.body || "",
          deepLink: payload.data?.url || "/",
          targetResourceId: (payload.data?.postId as string) || undefined,
          isRead: false,
          createdAt: new Date().toISOString(),
          actor: {
            id: actorId,
            username: (payload.data?.actorUsername as string) || "user",
            fullName: actorFullName,
            avatarUrl: (payload.data?.actorAvatarUrl as string) || payload.icon,
          },
        };

        return {
          ...old,
          notifications: [
            syntheticNotif,
            ...old.notifications.filter((n) => n.id !== syntheticNotif.id),
          ],
          unreadCount: newUnreadCount,
          totalElements: old.totalElements + 1,
        };
      });
      // 2. Refetch in background to sync authoritative state from server.
      // Defer so the optimistic update is not overwritten by a refetch that
      // may race the backend's INSERT for the very notification we just
      // received over push.
      setTimeout(() => {
        void queryClient.invalidateQueries({ queryKey });
      }, 600);
    };

    // The SW posts the authoritative notifications feed (fetched with the
    // JWT it read from IndexedDB) under this message type. When it lands we
    // replace the entire cache in one shot — no optimistic merge, no
    // re-render storm — because the SW is the one that did the network IO
    // and we trust its payload as ground truth.
    const onNotificationsRefreshed = (feed: NotificationListResponseDto) => {
      if (!feed) return;
      queryClient.setQueryData<NotificationListResponseDto>(queryKey, feed);
    };

    // 1. Observer: Listen to BroadcastChannel from Service Worker
    let broadcastChannel: BroadcastChannel | null = null;
    if (typeof BroadcastChannel !== "undefined") {
      try {
        broadcastChannel = new BroadcastChannel("wyrdly-notifications");
        broadcastChannel.onmessage = (event: MessageEvent) => {
          if (event.data?.type === "wyrdly:push-received") {
            onPushReceived(event.data.payload, event.data.eventId);
          } else if (event.data?.type === "wyrdly:notifications-refreshed") {
            onNotificationsRefreshed(event.data.payload);
          }
        };
      } catch {
        /* BroadcastChannel fallback */
      }
    }

    // 2. Observer: Listen to navigator.serviceWorker message events
    const handleSwMessage = (event: MessageEvent) => {
      if (event.data?.type === "wyrdly:push-received") {
        onPushReceived(event.data.payload, event.data.eventId);
      } else if (event.data?.type === "wyrdly:notifications-refreshed") {
        onNotificationsRefreshed(event.data.payload);
      }
    };
    if (typeof navigator !== "undefined" && "serviceWorker" in navigator) {
      navigator.serviceWorker.addEventListener("message", handleSwMessage);
    }

    // 3. Fallback: when the tab becomes visible again, ask the SW to fetch
    // the authoritative feed rather than doing the XHR from the page
    // itself. The page's event loop is throttled in background; the SW's
    // is not, so the SW is the right place to make the request. The SW
    // then posts the data back via `wyrdly:notifications-refreshed`,
    // which lands on the same listener above. As an extra safety net we
    // also invalidate the query so a missing SW reply still triggers a
    // page-side refetch.
    const handleVisibilityChange = () => {
      if (
        typeof document === "undefined" ||
        document.visibilityState !== "visible"
      ) {
        return;
      }
      const sw = navigator.serviceWorker;
      if (sw && sw.controller && typeof sw.controller.postMessage === "function") {
        sw.controller.postMessage({ type: "refresh-now" });
      }
      void queryClient.invalidateQueries({ queryKey });
    };
    if (typeof document !== "undefined") {
      document.addEventListener("visibilitychange", handleVisibilityChange);
    }

    return () => {
      if (broadcastChannel) {
        broadcastChannel.close();
      }
      if (typeof navigator !== "undefined" && "serviceWorker" in navigator) {
        navigator.serviceWorker.removeEventListener("message", handleSwMessage);
      }
      if (typeof document !== "undefined") {
        document.removeEventListener(
          "visibilitychange",
          handleVisibilityChange,
        );
      }
    };
  }, [enabled, queryClient, queryKey]);

  // Sync the OS-level app badge (the favicon counter shown by the UA) with
  // the authoritative unreadCount. The SW may have set a stale value when
  // the push was first delivered; the page is the source of truth and
  // reconciles on every state change. When the hook is disabled (e.g. on
  // logout) the badge is cleared so it does not leak across sessions.
  // Gated by isLoading so the initial mount (when unreadCount is 0 because
  // data is still undefined) does not briefly clear the badge and cause a
  // visible flicker.
  useEffect(() => {
    if (typeof navigator === "undefined") return;
    const nav = navigator as Navigator & {
      setAppBadge?: (count?: number) => Promise<void>;
      clearAppBadge?: () => Promise<void>;
    };
    if (typeof nav.setAppBadge !== "function") return;
    if (!enabled) {
      void nav.clearAppBadge?.().catch(() => undefined);
      return;
    }
    if (isLoading) return;
    if (unreadCount > 0) {
      void nav.setAppBadge(unreadCount).catch(() => undefined);
    } else {
      void nav.clearAppBadge?.().catch(() => undefined);
    }
  }, [enabled, unreadCount, isLoading]);

  const markReadMutation = useMutation({
    mutationFn: async (notificationId: string) => {
      if (notificationId.startsWith("push_")) {
        // Optimistic-only item not yet saved on backend; skip network call.
        return;
      }
      await apiClient.post(`/api/notifications/${notificationId}/read`);
    },
    onMutate: async (notificationId: string) => {
      await queryClient.cancelQueries({ queryKey });
      const previousData =
        queryClient.getQueryData<NotificationListResponseDto>(queryKey);

      if (previousData) {
        queryClient.setQueryData<NotificationListResponseDto>(queryKey, {
          ...previousData,
          notifications: previousData.notifications.map((n) =>
            n.id === notificationId ? { ...n, isRead: true } : n,
          ),
          unreadCount: Math.max(0, previousData.unreadCount - 1),
        });
      }

      return { previousData };
    },
    onError: (_err, _notificationId, context) => {
      if (context?.previousData) {
        queryClient.setQueryData(queryKey, context.previousData);
      }
      void queryClient.invalidateQueries({ queryKey });
    },
  });

  const markAllReadMutation = useMutation({
    mutationFn: async () => {
      await apiClient.post("/api/notifications/mark-all-read");
    },
    onMutate: async () => {
      await queryClient.cancelQueries({ queryKey });
      const previousData =
        queryClient.getQueryData<NotificationListResponseDto>(queryKey);

      if (previousData) {
        queryClient.setQueryData<NotificationListResponseDto>(queryKey, {
          ...previousData,
          notifications: previousData.notifications.map((n) => ({
            ...n,
            isRead: true,
          })),
          unreadCount: 0,
        });
      }

      return { previousData };
    },
    onError: (_err, _variables, context) => {
      if (context?.previousData) {
        queryClient.setQueryData(queryKey, context.previousData);
      }
      void queryClient.invalidateQueries({ queryKey });
    },
  });

  const refetch = useCallback(async (): Promise<void> => {
    await queryRefetch();
  }, [queryRefetch]);

  const markRead = useCallback(
    async (notificationId: string): Promise<void> => {
      await markReadMutation.mutateAsync(notificationId);
    },
    [markReadMutation],
  );

  const markAllRead = useCallback(async (): Promise<void> => {
    await markAllReadMutation.mutateAsync();
  }, [markAllReadMutation]);

  return {
    notifications,
    unreadCount,
    isLoading,
    error: error ?? null,
    refetch,
    markRead,
    markAllRead,
  };
};
