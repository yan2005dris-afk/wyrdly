import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { createElement } from "react";
import { useNotificationStream } from "./useNotificationStream";
import * as tokenStore from "../../../api/tokenStore";

describe("useNotificationStream", () => {
  let queryClient: QueryClient;
  const originalFetch = globalThis.fetch;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false, gcTime: 0 },
      },
    });
    vi.spyOn(tokenStore, "getAccessToken").mockReturnValue("mock-token-xyz");
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
    vi.restoreAllMocks();
  });

  const wrapper = ({ children }: { children: ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);

  it("does not connect if enabled is false", () => {
    const fetchMock = vi.fn();
    globalThis.fetch = fetchMock;

    renderHook(() => useNotificationStream({ enabled: false }), { wrapper });

    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("connects to /api/notifications/stream with Authorization and Accept headers", async () => {
    let capturedSignal: AbortSignal | undefined;
    const fetchMock = vi.fn().mockImplementation((_url, init) => {
      capturedSignal = init?.signal;
      return Promise.resolve({
        ok: true,
        body: {
          getReader: () => ({
            read: () => new Promise(() => {}), // keeps stream open
          }),
        },
      });
    });
    globalThis.fetch = fetchMock;

    const { unmount } = renderHook(
      () => useNotificationStream({ enabled: true }),
      {
        wrapper,
      },
    );

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining("/api/notifications/stream"),
        expect.objectContaining({
          headers: expect.objectContaining({
            Accept: "text/event-stream",
            Authorization: "Bearer mock-token-xyz",
          }),
        }),
      );
    });

    unmount();
    expect(capturedSignal?.aborted).toBe(true);
  });

  it("dispatches onNotification and invalidates queries on incoming SSE event", async () => {
    const onNotification = vi.fn();
    const invalidateSpy = vi.spyOn(queryClient, "invalidateQueries");

    const encoder = new TextEncoder();
    const eventPayload = JSON.stringify({
      id: "ntf_live_1",
      type: "GRAPH_FOLLOW",
      title: "Nuevo seguidor",
      body: "Juan comenzó a seguirte",
      isRead: false,
    });

    let hasRead = false;
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      body: {
        getReader: () => ({
          read: () => {
            if (!hasRead) {
              hasRead = true;
              return Promise.resolve({
                done: false,
                value: encoder.encode(`data: ${eventPayload}\n\n`),
              });
            }
            return new Promise(() => {}); // stay open
          },
        }),
      },
    });
    globalThis.fetch = fetchMock;

    renderHook(
      () =>
        useNotificationStream({
          enabled: true,
          onNotification,
        }),
      { wrapper },
    );

    await waitFor(() => {
      expect(onNotification).toHaveBeenCalledWith(
        expect.objectContaining({
          id: "ntf_live_1",
          type: "GRAPH_FOLLOW",
        }),
      );
      expect(invalidateSpy).toHaveBeenCalledWith({
        queryKey: ["notifications"],
      });
    });
  });
});
