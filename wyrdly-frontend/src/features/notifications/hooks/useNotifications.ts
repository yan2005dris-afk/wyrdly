/* eslint-disable react-hooks/set-state-in-effect --
   This hook intentionally calls setState after an async fetch inside a useEffect
   (the standard data-fetching pattern). The setState calls are guarded by
   cancelledRef so an unmount during a pending request does not cause a
   "setState on unmounted component" warning. */
import { useCallback, useEffect, useRef, useState } from "react";
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

/**
 * Manages the in-app notification feed: fetches the user's notifications on mount,
 * observes incoming Web Push events reactively, and exposes optimistic mark-read helpers.
 */
export const useNotifications = (
  options: UseNotificationsOptions = {},
): UseNotificationsResult => {
  const { enabled = true, pageSize = 20 } = options;
  const [notifications, setNotifications] = useState<
    NotificationListResponseDto["notifications"]
  >([]);
  const [unreadCount, setUnreadCount] = useState<number>(0);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [error, setError] = useState<Error | null>(null);
  const cancelledRef = useRef<boolean>(false);

  const fetchOnce = useCallback(async (): Promise<void> => {
    setIsLoading(true);
    try {
      const response = await apiClient.get<NotificationListResponseDto>(
        `/api/notifications?page=0&pageSize=${pageSize}`,
      );
      if (cancelledRef.current) return;
      setNotifications(response.data.notifications);
      setUnreadCount(response.data.unreadCount);
      setError(null);
    } catch (err) {
      if (cancelledRef.current) return;
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      if (!cancelledRef.current) {
        setIsLoading(false);
      }
    }
  }, [pageSize]);

  useEffect(() => {
    cancelledRef.current = false;
    if (!enabled) {
      return;
    }

    // Initial fetch
    void fetchOnce();

    const onPushReceived = () => {
      // 1. Optimistic feedback: immediately increment the badge so the UI responds without waiting for network I/O
      setUnreadCount((prev) => prev + 1);
      // 2. Fetch fresh list from server to populate popover
      void fetchOnce();
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

    // 3. Refetch on tab focus / visibilitychange when tab becomes visible again
    const handleVisibilityChange = () => {
      if (document.visibilityState === "visible") {
        void fetchOnce();
      }
    };
    if (typeof document !== "undefined") {
      document.addEventListener("visibilitychange", handleVisibilityChange);
    }

    return () => {
      cancelledRef.current = true;
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
  }, [enabled, fetchOnce]);

  const markRead = useCallback(
    async (notificationId: string): Promise<void> => {
      setNotifications((prev) =>
        prev.map((n) => (n.id === notificationId ? { ...n, isRead: true } : n)),
      );
      setUnreadCount((prev) => Math.max(0, prev - 1));
      try {
        await apiClient.post(`/api/notifications/${notificationId}/read`);
      } catch (err) {
        // Rollback by refetching; simpler than inverting optimistic update.
        await fetchOnce();
        throw err instanceof Error ? err : new Error(String(err));
      }
    },
    [fetchOnce],
  );

  const markAllRead = useCallback(async (): Promise<void> => {
    setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })));
    setUnreadCount(0);
    try {
      await apiClient.post("/api/notifications/mark-all-read");
    } catch (err) {
      await fetchOnce();
      throw err instanceof Error ? err : new Error(String(err));
    }
  }, [fetchOnce]);

  return {
    notifications,
    unreadCount,
    isLoading,
    error,
    refetch: fetchOnce,
    markRead,
    markAllRead,
  };
};
