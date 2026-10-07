import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, renderHook } from "@testing-library/react";

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

describe("useNotifications", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("fetches the list on mount when enabled", async () => {
    mockedGet.mockResolvedValueOnce({ data: baseResponse });
    const { result } = renderHook(() => useNotifications());

    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(mockedGet).toHaveBeenCalledWith(
      "/api/notifications?page=0&pageSize=20",
    );
    expect(result.current.notifications).toHaveLength(2);
    expect(result.current.unreadCount).toBe(1);
    expect(result.current.error).toBeNull();
  });

  it("does not fetch when enabled=false", async () => {
    mockedGet.mockResolvedValueOnce({ data: baseResponse });
    const { result } = renderHook(() => useNotifications({ enabled: false }));

    await act(async () => {
      await Promise.resolve();
    });

    expect(mockedGet).not.toHaveBeenCalled();
    expect(result.current.notifications).toHaveLength(0);
  });

  it("polls every 30 seconds", async () => {
    mockedGet.mockResolvedValue({ data: baseResponse });
    renderHook(() => useNotifications());

    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });
    expect(mockedGet).toHaveBeenCalledTimes(1);

    await act(async () => {
      vi.advanceTimersByTime(30_000);
      await Promise.resolve();
      await Promise.resolve();
    });
    expect(mockedGet).toHaveBeenCalledTimes(2);

    await act(async () => {
      vi.advanceTimersByTime(30_000);
      await Promise.resolve();
      await Promise.resolve();
    });
    expect(mockedGet).toHaveBeenCalledTimes(3);
  });

  it("captures error and exposes it", async () => {
    mockedGet.mockRejectedValueOnce(new Error("network down"));
    const { result } = renderHook(() => useNotifications());

    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(result.current.error).toBeInstanceOf(Error);
    expect(result.current.error?.message).toBe("network down");
    expect(result.current.notifications).toHaveLength(0);
  });

  it("markRead updates state optimistically and calls the API", async () => {
    mockedGet.mockResolvedValueOnce({ data: baseResponse });
    mockedPost.mockResolvedValueOnce({ data: null });

    const { result } = renderHook(() => useNotifications());
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(result.current.unreadCount).toBe(1);

    await act(async () => {
      await result.current.markRead("ntf_1");
    });

    expect(result.current.notifications[0]?.isRead).toBe(true);
    expect(result.current.unreadCount).toBe(0);
    expect(mockedPost).toHaveBeenCalledWith("/api/notifications/ntf_1/read");
  });

  it("markAllRead zeros the badge and calls the API", async () => {
    mockedGet.mockResolvedValueOnce({ data: baseResponse });
    mockedPost.mockResolvedValueOnce({ data: { updated: 1 } });

    const { result } = renderHook(() => useNotifications());
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });

    await act(async () => {
      await result.current.markAllRead();
    });

    expect(result.current.unreadCount).toBe(0);
    expect(result.current.notifications.every((n) => n.isRead)).toBe(true);
    expect(mockedPost).toHaveBeenCalledWith("/api/notifications/mark-all-read");
  });

  it("markRead rolls back by refetching when the API call fails", async () => {
    mockedGet
      .mockResolvedValueOnce({ data: baseResponse })
      .mockResolvedValueOnce({ data: { ...baseResponse, unreadCount: 1 } });
    mockedPost.mockRejectedValueOnce(new Error("server error"));

    const { result } = renderHook(() => useNotifications());
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });

    await act(async () => {
      try {
        await result.current.markRead("ntf_1");
      } catch {
        // expected
      }
    });

    // After the failed POST the hook refetches; the server-reported unreadCount wins.
    expect(result.current.unreadCount).toBe(1);
  });
});
