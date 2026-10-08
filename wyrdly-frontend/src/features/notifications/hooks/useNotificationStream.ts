import { useEffect, useRef } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { API_BASE_URL } from "../../../api/axios";
import { getAccessToken } from "../../../api/tokenStore";
import type { SocialNotification } from "../types";

export interface UseNotificationStreamOptions {
  /** When false the stream is dormant and does not connect. Defaults to true. */
  readonly enabled?: boolean;
  /** Optional callback invoked when a notification arrives over the stream. */
  readonly onNotification?: (notification: SocialNotification) => void;
  /** Optional query key prefix to invalidate. Defaults to ["notifications"]. */
  readonly queryKey?: readonly unknown[];
}

/**
 * Connects to the backend Server-Sent Events (SSE) notification stream.
 *
 * Provides real-time in-app delivery with automatic reconnection, query invalidation,
 * and zero reliance on OS push permissions or Service Worker background channels.
 */
export const useNotificationStream = (
  options: UseNotificationStreamOptions = {},
): void => {
  const {
    enabled = true,
    onNotification,
    queryKey = ["notifications"],
  } = options;
  const queryClient = useQueryClient();
  const onNotificationRef = useRef(onNotification);

  useEffect(() => {
    onNotificationRef.current = onNotification;
  }, [onNotification]);

  useEffect(() => {
    if (!enabled) return;

    let isAborted = false;
    let abortController = new AbortController();
    let reconnectTimeout: ReturnType<typeof setTimeout> | null = null;
    let delay = 1000;

    const connect = async () => {
      if (isAborted) return;
      abortController = new AbortController();

      const token = getAccessToken();
      const headers: Record<string, string> = {
        Accept: "text/event-stream",
      };
      if (token) {
        headers.Authorization = `Bearer ${token}`;
      }

      const streamUrl = `${API_BASE_URL}/api/notifications/stream`;

      try {
        const response = await fetch(streamUrl, {
          headers,
          signal: abortController.signal,
          credentials: "include",
        });

        if (!response.ok) {
          throw new Error(
            `SSE stream connection failed with status ${response.status}`,
          );
        }

        // Successfully connected, reset backoff delay
        delay = 1000;

        const reader = response.body?.getReader();
        if (!reader) {
          throw new Error("Response body is not readable");
        }

        const decoder = new TextDecoder();
        let buffer = "";

        while (!isAborted) {
          const { done, value } = await reader.read();
          if (done) break;

          buffer += decoder.decode(value, { stream: true });
          const parts = buffer.split("\n\n");
          buffer = parts.pop() ?? "";

          for (const message of parts) {
            const dataLine = message
              .split("\n")
              .find((line) => line.startsWith("data:"));

            if (dataLine) {
              const rawData = dataLine.slice(5).trim();
              if (rawData) {
                try {
                  const parsed = JSON.parse(rawData) as SocialNotification;
                  onNotificationRef.current?.(parsed);
                  void queryClient.invalidateQueries({ queryKey });
                } catch {
                  // Fallback: invalidate query even if JSON parse fails
                  void queryClient.invalidateQueries({ queryKey });
                }
              }
            }
          }
        }
      } catch (err: unknown) {
        if (
          isAborted ||
          (err instanceof DOMException && err.name === "AbortError")
        ) {
          return;
        }
      }

      if (!isAborted) {
        // Reconnect with exponential backoff capped at 30s
        reconnectTimeout = setTimeout(() => {
          delay = Math.min(delay * 1.5, 30000);
          void connect();
        }, delay);
      }
    };

    void connect();

    return () => {
      isAborted = true;
      abortController.abort();
      if (reconnectTimeout) {
        clearTimeout(reconnectTimeout);
      }
    };
  }, [enabled, queryClient, queryKey]);
};
