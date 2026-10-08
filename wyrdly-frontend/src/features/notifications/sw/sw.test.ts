/**
 * Exercises the pure handlers exposed by `public/sw.js` against a mocked
 * `self`. The SW file is loaded once per test; its module-level `addEventListener`
 * calls are guarded by `isServiceWorkerContext()` so they stay inert under
 * Vitest's jsdom environment.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

type SW = {
  handlePush: (event: unknown) => Promise<void>;
  broadcastPushReceived: (payload: unknown) => Promise<void>;
  handleNotificationClick: (event: unknown) => Promise<void>;
  handleSubscriptionChange: (event: unknown) => Promise<void>;
  handleInstall: (event: unknown) => void;
  handleActivate: (event: unknown) => void;
  urlBase64ToUint8Array: (input: string) => Uint8Array;
};

let cachedSW: SW | null = null;

const loadSW = async (): Promise<SW> => {
  // Always reset the module cache before importing. The SW keeps a
  // module-level `pushChannel` for performance, but in tests we need a
  // fresh module instance per run so the `BroadcastChannel` mock injected
  // via globalThis on the current test is the one the SW actually uses.
  vi.resetModules();
  cachedSW = null;
  await import("../../../../public/sw.js");
  const w = globalThis as unknown as { __wyrdlySW?: SW };
  if (!w.__wyrdlySW) {
    throw new Error("Service worker handlers were not exposed on globalThis");
  }
  cachedSW = w.__wyrdlySW;
  return cachedSW;
};

const RESTORE_KEYS = [
  "self",
  "registration",
  "clients",
  "ServiceWorkerGlobalScope",
  "fetch",
] as const;

const restoreSelf = (
  snapshot: Record<(typeof RESTORE_KEYS)[number], unknown>,
): void => {
  const target = globalThis as unknown as Record<string, unknown>;
  for (const key of RESTORE_KEYS) {
    delete target[key];
  }
  for (const [key, value] of Object.entries(snapshot)) {
    if (value === undefined) {
      delete target[key];
    } else {
      Object.defineProperty(target, key, {
        value,
        configurable: true,
        writable: true,
        enumerable: true,
      });
    }
  }
};

describe("service worker handlers", () => {
  let selfSnapshot: Record<(typeof RESTORE_KEYS)[number], unknown>;

  beforeEach(() => {
    const target = globalThis as unknown as Record<string, unknown>;
    selfSnapshot = {} as Record<(typeof RESTORE_KEYS)[number], unknown>;
    for (const key of RESTORE_KEYS) {
      selfSnapshot[key] = target[key];
    }
  });

  afterEach(() => {
    restoreSelf(selfSnapshot);
    vi.restoreAllMocks();
  });

  it("urlBase64ToUint8Array decodes a base64url string into a Uint8Array", async () => {
    const sw = await loadSW();
    const out = sw.urlBase64ToUint8Array("AQID");
    expect(out).toBeInstanceOf(Uint8Array);
    expect(Array.from(out)).toEqual([1, 2, 3]);
  });

  it("handlePush renders a notification with the payload fields", async () => {
    const sw = await loadSW();
    const showNotification = vi.fn().mockResolvedValue(undefined);
    (globalThis as unknown as { registration: unknown }).registration = {
      showNotification,
    };
    (globalThis as unknown as { self: unknown }).self = globalThis;

    const event = {
      data: {
        json: () => ({
          title: "Nuevo seguidor",
          body: "Ana comenzó a seguirte",
          icon: "/icons/x.png",
          badge: "/icons/b.png",
          data: { url: "/profile/ana", type: "GRAPH_FOLLOW" },
        }),
      },
    };

    await sw.handlePush(event);

    expect(showNotification).toHaveBeenCalledTimes(1);
    const [title, options] = showNotification.mock.calls[0] as [
      string,
      Record<string, unknown>,
    ];
    expect(title).toBe("Nuevo seguidor");
    expect(options.body).toBe("Ana comenzó a seguirte");
    expect(options.icon).toBe("/icons/x.png");
    expect(options.badge).toBe("/icons/b.png");
    expect(options.tag).toBe("type-GRAPH_FOLLOW");
    expect(options.data).toEqual({ url: "/profile/ana", type: "GRAPH_FOLLOW" });
  });

  it("handlePush falls back to text() when json() throws", async () => {
    const sw = await loadSW();
    const showNotification = vi.fn().mockResolvedValue(undefined);
    (globalThis as unknown as { registration: unknown }).registration = {
      showNotification,
    };
    (globalThis as unknown as { self: unknown }).self = globalThis;

    const event = {
      data: {
        json: () => {
          throw new Error("not json");
        },
        text: () => "raw text",
      },
    };

    await sw.handlePush(event);

    expect(showNotification).toHaveBeenCalledTimes(1);
    const [, options] = showNotification.mock.calls[0] as [
      string,
      Record<string, unknown>,
    ];
    expect(options.body).toBe("raw text");
  });

  it("handlePush syncs the OS-level app badge with the visible notification count", async () => {
    const sw = await loadSW();
    const showNotification = vi.fn().mockResolvedValue(undefined);
    const setAppBadge = vi.fn().mockResolvedValue(undefined);
    const clearAppBadge = vi.fn().mockResolvedValue(undefined);
    const getNotifications = vi.fn().mockResolvedValue([{}, {}]);
    const matchAll = vi.fn().mockResolvedValue([]);

    (globalThis as unknown as { registration: unknown }).registration = {
      showNotification,
      getNotifications,
    };
    (globalThis as unknown as { self: unknown }).self = {
      ...globalThis,
      navigator: { setAppBadge, clearAppBadge },
      clients: { matchAll },
    };

    const event = {
      data: {
        json: () => ({ title: "X", body: "Y", data: { type: "POST_LIKE" } }),
      },
    };

    await sw.handlePush(event);

    expect(showNotification).toHaveBeenCalledTimes(1);
    expect(getNotifications).toHaveBeenCalledTimes(1);
    // 2 currently visible notifications in the registration → badge = 2.
    expect(setAppBadge).toHaveBeenCalledWith(2);
    expect(clearAppBadge).not.toHaveBeenCalled();
  });

  it("handlePush clears the OS-level app badge when no notifications are visible", async () => {
    const sw = await loadSW();
    const showNotification = vi.fn().mockResolvedValue(undefined);
    const setAppBadge = vi.fn().mockResolvedValue(undefined);
    const clearAppBadge = vi.fn().mockResolvedValue(undefined);
    const getNotifications = vi.fn().mockResolvedValue([]);
    const matchAll = vi.fn().mockResolvedValue([]);

    (globalThis as unknown as { registration: unknown }).registration = {
      showNotification,
      getNotifications,
    };
    (globalThis as unknown as { self: unknown }).self = {
      ...globalThis,
      navigator: { setAppBadge, clearAppBadge },
      clients: { matchAll },
    };

    const event = {
      data: {
        json: () => ({ title: "X", body: "Y" }),
      },
    };

    await sw.handlePush(event);

    expect(setAppBadge).not.toHaveBeenCalled();
    expect(clearAppBadge).toHaveBeenCalledTimes(1);
  });

  it("handlePush silently skips badge updates when the UA does not expose setAppBadge", async () => {
    const sw = await loadSW();
    const showNotification = vi.fn().mockResolvedValue(undefined);
    const getNotifications = vi.fn().mockResolvedValue([{}, {}, {}]);
    const matchAll = vi.fn().mockResolvedValue([]);

    (globalThis as unknown as { registration: unknown }).registration = {
      showNotification,
      getNotifications,
    };
    // No navigator.setAppBadge on this UA (Safari iOS pre-16.4, some Firefox builds).
    (globalThis as unknown as { self: unknown }).self = {
      ...globalThis,
      navigator: {},
      clients: { matchAll },
    };

    const event = {
      data: {
        json: () => ({ title: "X", body: "Y" }),
      },
    };

    // Must not throw even though the badge API is missing.
    await expect(sw.handlePush(event)).resolves.toBeUndefined();
    expect(showNotification).toHaveBeenCalledTimes(1);
  });

  it("broadcastPushReceived notifies BroadcastChannel and clients.matchAll", async () => {
    const sw = await loadSW();
    const postMessageClient = vi.fn();
    (globalThis as unknown as { self: unknown }).self = {
      clients: {
        matchAll: vi
          .fn()
          .mockResolvedValue([{ postMessage: postMessageClient }]),
      },
    };

    const channelPostMessage = vi.fn();
    const channelClose = vi.fn();
    class MockBroadcastChannel {
      readonly name: string;
      constructor(name: string) {
        this.name = name;
      }
      postMessage = channelPostMessage;
      close = channelClose;
    }
    const origBroadcastChannel = globalThis.BroadcastChannel;
    (globalThis as unknown as { BroadcastChannel: unknown }).BroadcastChannel =
      MockBroadcastChannel;

    try {
      await sw.broadcastPushReceived({ title: "Test", body: "Hello" });
      expect(channelPostMessage).toHaveBeenCalledWith(
        expect.objectContaining({
          type: "wyrdly:push-received",
          payload: { title: "Test", body: "Hello" },
        }),
      );
      // The SW now reuses a single BroadcastChannel for its lifetime instead
      // of creating-and-closing one per push. Verify the channel is not
      // closed, even after the previous 1s debounce window elapses.
      vi.useFakeTimers();
      vi.advanceTimersByTime(2000);
      vi.useRealTimers();
      expect(channelClose).not.toHaveBeenCalled();
      expect(postMessageClient).toHaveBeenCalledWith(
        expect.objectContaining({
          type: "wyrdly:push-received",
          payload: { title: "Test", body: "Hello" },
        }),
      );
    } finally {
      (
        globalThis as unknown as { BroadcastChannel: unknown }
      ).BroadcastChannel = origBroadcastChannel;
    }
  });

  it("handleNotificationClick focuses an existing tab and navigates when the URL differs", async () => {
    const sw = await loadSW();
    const focus = vi.fn().mockResolvedValue(undefined);
    const navigate = vi.fn().mockResolvedValue(undefined);
    const openWindow = vi.fn().mockResolvedValue(undefined);
    const close = vi.fn();

    (globalThis as unknown as { self: unknown }).self = {
      location: { origin: "https://app.wyrdly.com" },
      clients: {
        matchAll: vi
          .fn()
          .mockResolvedValue([
            { url: "https://app.wyrdly.com/feed", focus, navigate },
          ]),
        openWindow,
      },
    };

    const event = {
      notification: {
        data: { url: "/profile/ana" },
        close,
      },
    };

    await sw.handleNotificationClick(event);

    expect(close).toHaveBeenCalledTimes(1);
    expect(focus).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledWith("/profile/ana");
    expect(openWindow).not.toHaveBeenCalled();
  });

  it("handleNotificationClick opens a new window when no existing tab matches", async () => {
    const sw = await loadSW();
    const openWindow = vi.fn().mockResolvedValue(undefined);
    const close = vi.fn();

    (globalThis as unknown as { self: unknown }).self = {
      location: { origin: "https://app.wyrdly.com" },
      clients: {
        matchAll: vi.fn().mockResolvedValue([]),
        openWindow,
      },
    };

    const event = {
      notification: {
        data: { url: "/posts/abc" },
        close,
      },
    };

    await sw.handleNotificationClick(event);

    expect(close).toHaveBeenCalledTimes(1);
    expect(openWindow).toHaveBeenCalledWith("/posts/abc");
  });

  it("handleSubscriptionChange re-subscribes and POSTs the new subscription", async () => {
    const sw = await loadSW();
    const newSub = {
      toJSON: () => ({ endpoint: "https://push.example.com/new" }),
    };
    const subscribe = vi.fn().mockResolvedValue(newSub);
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce({ ok: true, text: () => Promise.resolve("AQID") })
      .mockResolvedValueOnce({ ok: true });

    (globalThis as unknown as { fetch: unknown }).fetch = fetchMock;
    (globalThis as unknown as { self: unknown }).self = {
      clients: { matchAll: vi.fn().mockResolvedValue([]) },
      registration: { pushManager: { subscribe } },
      location: { origin: "https://app.wyrdly.com" },
    };

    await sw.handleSubscriptionChange({});

    expect(subscribe).toHaveBeenCalledWith({
      userVisibleOnly: true,
      applicationServerKey: expect.any(Uint8Array),
    });
    expect(fetchMock).toHaveBeenCalledTimes(2);
    const [url, init] = fetchMock.mock.calls[1] as [
      string,
      Record<string, unknown>,
    ];
    expect(url).toBe("/api/notifications/subscribe");
    expect(init.method).toBe("POST");
    expect(JSON.parse(String(init.body))).toEqual({
      endpoint: "https://push.example.com/new",
    });
  });

  it("handleSubscriptionChange posts a message to clients when re-subscribe fails", async () => {
    const sw = await loadSW();
    const postMessage = vi.fn();
    const fetchMock = vi.fn().mockResolvedValue({
      ok: false,
      status: 500,
      statusText: "Server Error",
    });

    (globalThis as unknown as { fetch: unknown }).fetch = fetchMock;
    (globalThis as unknown as { self: unknown }).self = {
      clients: {
        matchAll: vi.fn().mockResolvedValue([{ postMessage }]),
      },
      registration: {
        pushManager: { subscribe: vi.fn() },
      },
      location: { origin: "https://app.wyrdly.com" },
    };

    await sw.handleSubscriptionChange({});

    expect(postMessage).toHaveBeenCalledTimes(1);
    const payload = postMessage.mock.calls[0]?.[0] as { type: string };
    expect(payload.type).toBe("push-subscription-change-failed");
  });

  it("handleInstall calls self.skipWaiting when available", async () => {
    const sw = await loadSW();
    const skipWaiting = vi.fn();
    (globalThis as unknown as { self: unknown }).self = { skipWaiting };

    sw.handleInstall({});

    expect(skipWaiting).toHaveBeenCalledTimes(1);
  });

  it("handleActivate calls clients.claim() via waitUntil when provided", async () => {
    const sw = await loadSW();
    const claim = vi.fn().mockResolvedValue(undefined);
    const waitUntil = vi.fn();
    (globalThis as unknown as { self: unknown }).self = {
      clients: { claim },
    };

    sw.handleActivate({ waitUntil });

    expect(waitUntil).toHaveBeenCalledTimes(1);
    // Drain the promise passed to waitUntil so claim is invoked.
    const passedPromise = (waitUntil.mock.calls[0] as [Promise<unknown>])[0];
    await passedPromise;
    expect(claim).toHaveBeenCalledTimes(1);
  });
});
