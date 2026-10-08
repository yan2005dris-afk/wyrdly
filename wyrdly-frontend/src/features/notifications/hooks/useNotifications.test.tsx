import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

import type { ReactNode } from "react";

vi.mock("../../../api/axios", () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
  },
}));

import { apiClient } from "../../../api/axios";
import { useNotifications } from "./useNotifications";
import type { NotificationListResponseDto } from "../types";

const mockedGet = vi.mocked(apiClient.get);
const mockedPost = vi.mocked(apiClient.post);

const baseResponse: NotificationListResponseDto = {
  notifications: [
    {
      id: "ntf_1",
      type: "GRAPH_FOLLOW",
      title: "Nuevo seguidor",
      body: "Bob comenzó a seguirte",
      deepLink: "/feed",
      isRead: false,
      createdAt: "2026-01-15T10:00:00Z",
      actor: { id: "usr_bob", username: "bob", fullName: "Bob Marley" },
    },
    {
      id: "ntf_2",
      type: "POST_LIKE",
      title: "Like",
      body: "Carol le dio Like",
      deepLink: "/posts/pst_1",
      targetResourceId: "pst_1",
      isRead: true,
      createdAt: "2026-01-15T09:00:00Z",
      actor: { id: "usr_carol", username: "carol", fullName: "Carol" },
    },
  ],
  unreadCount: 1,
  page: 0,
  pageSize: 20,
  totalElements: 2,
};

const createTestQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        gcTime: 0,
      },
    },
  });

const createWrapper = () => {
  const queryClient = createTestQueryClient();
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
};

describe("useNotifications", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("fetches the list on mount when enabled", async () => {
    mockedGet.mockResolvedValueOnce({ data: baseResponse });
    const { result } = renderHook(() => useNotifications(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
      expect(result.current.notifications).toHaveLength(2);
    });

    expect(mockedGet).toHaveBeenCalledWith(
      "/api/notifications?page=0&pageSize=20",
    );
    expect(result.current.unreadCount).toBe(1);
    expect(result.current.error).toBeNull();
  });

  it("does not fetch when enabled=false", async () => {
    const { result } = renderHook(() => useNotifications({ enabled: false }), {
      wrapper: createWrapper(),
    });

    await act(async () => {
      await Promise.resolve();
    });

    expect(mockedGet).not.toHaveBeenCalled();
    expect(result.current.notifications).toHaveLength(0);
  });

  it("captures error and exposes it", async () => {
    mockedGet.mockRejectedValueOnce(new Error("network down"));
    const { result } = renderHook(() => useNotifications(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.error).toBeInstanceOf(Error);
    });

    expect(result.current.error?.message).toBe("network down");
    expect(result.current.notifications).toHaveLength(0);
  });

  it("markRead updates state optimistically and calls the API", async () => {
    mockedGet.mockResolvedValueOnce({ data: baseResponse });
    mockedPost.mockResolvedValueOnce({ data: null });

    const { result } = renderHook(() => useNotifications(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.notifications).toHaveLength(2);
    });

    expect(result.current.unreadCount).toBe(1);

    await act(async () => {
      await result.current.markRead("ntf_1");
    });

    await waitFor(() => {
      expect(result.current.notifications[0]?.isRead).toBe(true);
      expect(result.current.unreadCount).toBe(0);
    });
    expect(mockedPost).toHaveBeenCalledWith("/api/notifications/ntf_1/read");
  });

  it("markAllRead zeros the badge and calls the API", async () => {
    mockedGet.mockResolvedValueOnce({ data: baseResponse });
    mockedPost.mockResolvedValueOnce({ data: { updated: 1 } });

    const { result } = renderHook(() => useNotifications(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.notifications).toHaveLength(2);
    });

    await act(async () => {
      await result.current.markAllRead();
    });

    await waitFor(() => {
      expect(result.current.unreadCount).toBe(0);
      expect(result.current.notifications.every((n) => n.isRead)).toBe(true);
    });
    expect(mockedPost).toHaveBeenCalledWith("/api/notifications/mark-all-read");
  });

  it("markRead rolls back when the API call fails", async () => {
    mockedGet
      .mockResolvedValueOnce({ data: baseResponse })
      .mockResolvedValueOnce({ data: { ...baseResponse, unreadCount: 1 } });
    mockedPost.mockRejectedValueOnce(new Error("server error"));

    const { result } = renderHook(() => useNotifications(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.notifications).toHaveLength(2);
    });

    await act(async () => {
      try {
        await result.current.markRead("ntf_1");
      } catch {
        // expected
      }
    });

    expect(result.current.unreadCount).toBe(1);
  });

  it("refetches reactively when a push message is received via BroadcastChannel", async () => {
    mockedGet.mockResolvedValue({ data: baseResponse });

    let channelListener: ((event: MessageEvent) => void) | null = null;
    class MockBroadcastChannel {
      readonly name: string;
      constructor(name: string) {
        this.name = name;
      }
      set onmessage(fn: (event: MessageEvent) => void) {
        channelListener = fn;
      }
      close = vi.fn();
    }
    const origBroadcastChannel = globalThis.BroadcastChannel;
    (globalThis as unknown as { BroadcastChannel: unknown }).BroadcastChannel =
      MockBroadcastChannel;

    try {
      renderHook(() => useNotifications(), {
        wrapper: createWrapper(),
      });
      await act(async () => {
        await Promise.resolve();
      });
      expect(mockedGet).toHaveBeenCalledTimes(1);

      // Simulate incoming push event broadcast
      await act(async () => {
        channelListener?.({
          data: { type: "wyrdly:push-received" },
        } as MessageEvent);
        await Promise.resolve();
      });

      expect(mockedGet).toHaveBeenCalledTimes(2);
    } finally {
      (
        globalThis as unknown as { BroadcastChannel: unknown }
      ).BroadcastChannel = origBroadcastChannel;
    }
  });

  it("refetches reactively when navigator.serviceWorker fires a push message", async () => {
    mockedGet.mockResolvedValue({ data: baseResponse });

    let swMessageListener: ((event: MessageEvent) => void) | null = null;
    const addEventListener = vi.fn(
      (event: string, fn: (e: MessageEvent) => void) => {
        if (event === "message") swMessageListener = fn;
      },
    );
    const removeEventListener = vi.fn();

    const origNavigator = globalThis.navigator;
    Object.defineProperty(globalThis, "navigator", {
      value: {
        ...origNavigator,
        serviceWorker: {
          addEventListener,
          removeEventListener,
        },
      },
      configurable: true,
      writable: true,
    });

    try {
      renderHook(() => useNotifications(), {
        wrapper: createWrapper(),
      });
      await act(async () => {
        await Promise.resolve();
      });
      expect(mockedGet).toHaveBeenCalledTimes(1);

      // Simulate incoming serviceWorker postMessage
      await act(async () => {
        swMessageListener?.({
          data: { type: "wyrdly:push-received" },
        } as MessageEvent);
        await Promise.resolve();
      });

      expect(mockedGet).toHaveBeenCalledTimes(2);
    } finally {
      Object.defineProperty(globalThis, "navigator", {
        value: origNavigator,
        configurable: true,
        writable: true,
      });
    }
  });
});
