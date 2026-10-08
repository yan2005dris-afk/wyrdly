import { useCallback, useEffect, useMemo } from "react";
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

    const onPushReceived = (payload?: PushReceivedEventData) => {
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

        const syntheticNotif: NotificationDto = {
          id: `push_${Date.now()}`,
          type: (payload.data?.type as NotificationDto["type"]) || "POST_LIKE",
          title: payload.title,
          body: payload.body || "",
          deepLink: payload.data?.url || "/",
          targetResourceId: (payload.data?.postId as string) || undefined,
          isRead: false,
          createdAt: new Date().toISOString(),
          actor: {
            id:
              (payload.data?.actorId as string) ||
              (payload.data?.reactorId as string) ||
              "usr_push",
            username: (payload.data?.actorUsername as string) || "user",
            fullName: (payload.data?.actorFullName as string) || payload.title,
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

    // 1. Observer: Listen to BroadcastChannel from Service Worker
    let broadcastChannel: BroadcastChannel | null = null;
    if (typeof BroadcastChannel !== "undefined") {
      try {
        broadcastChannel = new BroadcastChannel("wyrdly-notifications");
        broadcastChannel.onmessage = (event: MessageEvent) => {
          if (event.data?.type === "wyrdly:push-received") {
            onPushReceived(event.data.payload);
          }
        };
      } catch {
        /* BroadcastChannel fallback */
      }
    }

    // 2. Observer: Listen to navigator.serviceWorker message events
    const handleSwMessage = (event: MessageEvent) => {
      if (event.data?.type === "wyrdly:push-received") {
        onPushReceived(event.data.payload);
      }
    };
    if (typeof navigator !== "undefined" && "serviceWorker" in navigator) {
      navigator.serviceWorker.addEventListener("message", handleSwMessage);
    }

    return () => {
      if (broadcastChannel) {
        broadcastChannel.close();
      }
      if (typeof navigator !== "undefined" && "serviceWorker" in navigator) {
        navigator.serviceWorker.removeEventListener("message", handleSwMessage);
      }
    };
  }, [enabled, queryClient, queryKey]);

  const markReadMutation = useMutation({
    mutationFn: async (notificationId: string) => {
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
