import { renderHook, act } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { useChatWebSocket } from "./useChatWebSocket";

class MockWebSocket {
  static OPEN = 1;
  static instances: MockWebSocket[] = [];

  readonly url: string;
  readyState = MockWebSocket.OPEN;
  onopen: (() => void) | null = null;
  onmessage: ((event: { data: string }) => void) | null = null;
  onerror: (() => void) | null = null;
  onclose: ((event: { code: number }) => void) | null = null;
  closeCalled = false;

  constructor(url: string) {
    this.url = url;
    MockWebSocket.instances.push(this);
  }

  send() {
    // No-op: frames are driven manually through the handlers.
  }

  close() {
    this.closeCalled = true;
  }
}

const lastSocket = () =>
  MockWebSocket.instances[MockWebSocket.instances.length - 1];

describe("useChatWebSocket", () => {
  beforeEach(() => {
    MockWebSocket.instances = [];
    vi.stubGlobal("WebSocket", MockWebSocket);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("connects with the token in the URL and reports connected on open", () => {
    const { result } = renderHook(() => useChatWebSocket({ token: "jwt-123" }));

    expect(lastSocket().url).toContain("token=jwt-123");

    act(() => {
      lastSocket().onopen?.();
    });

    expect(result.current.isConnected).toBe(true);
    expect(result.current.error).toBeNull();
  });

  it("forwards backend ERROR frames to onError", () => {
    const onError = vi.fn();
    const { result } = renderHook(() =>
      useChatWebSocket({ token: "jwt-123", onError }),
    );

    act(() => {
      lastSocket().onmessage?.({
        data: JSON.stringify({ action: "ERROR", message: "Rate limited" }),
      });
    });

    expect(onError).toHaveBeenCalledWith("Rate limited");
    expect(result.current.error).toBe("Rate limited");
  });

  it("falls back to a default message when the ERROR frame has no text", () => {
    const onError = vi.fn();
    renderHook(() => useChatWebSocket({ token: "jwt-123", onError }));

    act(() => {
      lastSocket().onmessage?.({ data: JSON.stringify({ action: "ERROR" }) });
    });

    expect(onError).toHaveBeenCalledWith("WebSocket chat error");
  });

  it("reports connection errors to onError", () => {
    const onError = vi.fn();
    const { result } = renderHook(() =>
      useChatWebSocket({ token: "jwt-123", onError }),
    );

    act(() => {
      lastSocket().onerror?.();
    });

    expect(onError).toHaveBeenCalledWith("WebSocket connection error");
    expect(result.current.error).toBe("WebSocket connection error");
  });

  it("reports unauthorized token close codes to onError", () => {
    const onError = vi.fn();
    renderHook(() => useChatWebSocket({ token: "jwt-123", onError }));

    act(() => {
      lastSocket().onclose?.({ code: 4401 });
    });

    expect(onError).toHaveBeenCalledWith(
      "Unauthorized token (close code 4401)",
    );
  });

  it("stays silent on regular close codes", () => {
    const onError = vi.fn();
    renderHook(() => useChatWebSocket({ token: "jwt-123", onError }));

    act(() => {
      lastSocket().onclose?.({ code: 1000 });
    });

    expect(onError).not.toHaveBeenCalled();
  });

  it("still delivers incoming chat messages to onMessageReceived", () => {
    const onMessageReceived = vi.fn();
    renderHook(() => useChatWebSocket({ token: "jwt-123", onMessageReceived }));

    const message = {
      id: "m-1",
      senderId: "user-alice",
      recipientId: "user-current",
      content: "Hello!",
      sentAt: "2026-10-02T10:00:00Z",
    };

    act(() => {
      lastSocket().onmessage?.({
        data: JSON.stringify({ action: "NEW_MESSAGE", message }),
      });
    });

    expect(onMessageReceived).toHaveBeenCalledWith(message);
  });
});
