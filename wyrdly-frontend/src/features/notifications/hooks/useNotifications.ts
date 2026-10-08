import { useCallback, useEffect } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "../../../api/axios";
import type { NotificationListResponseDto } from "../types";

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
  const queryKey = notificationsQueryKey(pageSize);

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

    const onPushReceived = () => {
      // 1. Optimistic feedback: update cached unread count immediately
      queryClient.setQueryData<NotificationListResponseDto>(queryKey, (old) => {
        if (!old) return old;
        return {
          ...old,
          unreadCount: old.unreadCount + 1,
        };
      });
      // 2. Invalidate query to fetch fresh notification payload from server
      void queryClient.invalidateQueries({ queryKey });
    };

    // 1. Observer: Listen to BroadcastChannel from Service Worker
    let broadcastChannel: BroadcastChannel | null = null;
    if (typeof BroadcastChannel !== "undefined") {
      try {
        broadcastChannel = new BroadcastChannel("wyrdly-notifications");
        broadcastChannel.onmessage = (event: MessageEvent) => {
          if (event.data?.type === "wyrdly:push-received") {
            onPushReceived();
          }
        };
      } catch {
        /* BroadcastChannel fallback */
      }
    }

    // 2. Observer: Listen to navigator.serviceWorker message events
    const handleSwMessage = (event: MessageEvent) => {
      if (event.data?.type === "wyrdly:push-received") {
        onPushReceived();
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
