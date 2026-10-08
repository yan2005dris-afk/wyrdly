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

  it("handlePush fetches the authoritative feed and broadcasts notifications-refreshed", async () => {
    const sw = await loadSW();
    const showNotification = vi.fn().mockResolvedValue(undefined);
    const setAppBadge = vi.fn().mockResolvedValue(undefined);
    const getNotifications = vi.fn().mockResolvedValue([]);
    const matchAll = vi.fn().mockResolvedValue([]);
    const channelPostMessage = vi.fn();

    const fakeFeed = {
      notifications: [
        {
          id: "ntf_real",
          type: "POST_LIKE",
          title: "Real",
          body: "real",
          deepLink: "/posts/pst_1",
          isRead: false,
          createdAt: "2026-01-15T12:00:00Z",
          actor: { id: "usr_x", username: "x", fullName: "X" },
        },
      ],
      unreadCount: 1,
      page: 0,
      pageSize: 20,
      totalElements: 1,
    };
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(fakeFeed),
    });

    (globalThis as unknown as { registration: unknown }).registration = {
      showNotification,
      getNotifications,
    };
    (globalThis as unknown as { fetch: unknown }).fetch = fetchMock;
    (globalThis as unknown as { self: unknown }).self = {
      registration: { showNotification, getNotifications },
      navigator: { setAppBadge },
      clients: { matchAll },
    };

    // Fake IndexedDB holding a JWT so the SW can read it.
    const store = new Map<string, string>([["jwt", "test-token-abc"]]);
    const storesByDb = new Map<string, Map<string, Map<string, string>>>();
    storesByDb.set("wyrdly-auth", new Map([["auth", store]]));
    const makeTx = (storeRef: Map<string, string>) => ({
      objectStore: () => ({
        get: (key: string) => {
          const r = {
            result: storeRef.get(key),
            onsuccess: null as ((e: unknown) => void) | null,
            onerror: null,
          };
          queueMicrotask(() => {
            if (r.onsuccess) r.onsuccess({ target: r });
          });
          return r;
        },
        put: (v: string, k: string) => storeRef.set(k, v),
        delete: (k: string) => storeRef.delete(k),
      }),
      oncomplete: null as ((e: unknown) => void) | null,
      onerror: null,
      onabort: null,
    });
    Object.defineProperty(globalThis, "indexedDB", {
      configurable: true,
      writable: true,
      value: {
        open: vi.fn(() => {
          const req = {
            result: {
              stores: storesByDb.get("wyrdly-auth")!,
              transaction: () => makeTx(store),
              close: () => {},
            },
            onupgradeneeded: null,
            onsuccess: null as ((e: unknown) => void) | null,
            onerror: null,
            onblocked: null,
          };
          queueMicrotask(() => {
            if (req.onsuccess) req.onsuccess({ target: req });
          });
          return req;
        }),
      },
    });

    try {
      // Replace the BroadcastChannel for this test so we can spy on the
      // refreshed broadcast without leaking listeners across tests.
      const origBroadcastChannel = globalThis.BroadcastChannel;
      class MockBroadcastChannel {
        readonly name: string;
        constructor(name: string) {
          this.name = name;
        }
        postMessage = channelPostMessage;
        close = vi.fn();
      }
      (globalThis as unknown as { BroadcastChannel: unknown }).BroadcastChannel =
        MockBroadcastChannel;

      try {
        const event = {
          data: {
            json: () => ({ title: "Push", body: "body" }),
          },
        };
        await sw.handlePush(event);

        // The SW fetched the feed with the JWT in the Authorization header.
        expect(fetchMock).toHaveBeenCalledTimes(1);
        const [url, init] = fetchMock.mock.calls[0] as [
          string,
          Record<string, unknown>,
        ];
        expect(url).toBe("/api/notifications?page=0&pageSize=20");
        const headers = init.headers as Record<string, string>;
        expect(headers.Authorization).toBe("Bearer test-token-abc");

        // The real feed was broadcast over the shared BroadcastChannel.
        expect(channelPostMessage).toHaveBeenCalledWith({
          type: "wyrdly:notifications-refreshed",
          payload: fakeFeed,
        });

        // The badge was synced with the authoritative unreadCount, not
        // the notification count in the registration (which is 0 here).
        expect(setAppBadge).toHaveBeenCalledWith(1);
      } finally {
        (globalThis as unknown as { BroadcastChannel: unknown }).BroadcastChannel =
          origBroadcastChannel;
      }
    } finally {
      try {
        delete (globalThis as { indexedDB?: unknown }).indexedDB;
      } catch {
        /* ignore */
      }
    }
  });

  it("handleRefreshRequest replies only to the requesting client with the fresh feed", async () => {
    const sw = await loadSW();
    const setAppBadge = vi.fn().mockResolvedValue(undefined);
    const fakeFeed = { notifications: [], unreadCount: 0, page: 0, pageSize: 20, totalElements: 0 };
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(fakeFeed),
    });
    const targetClient = { postMessage: vi.fn() };

    (globalThis as unknown as { fetch: unknown }).fetch = fetchMock;
    (globalThis as unknown as { self: unknown }).self = {
      navigator: { setAppBadge },
      clients: { matchAll: vi.fn().mockResolvedValue([]) },
    };

    const store = new Map<string, string>([["jwt", "tok"]]);
    const makeTx = (storeRef: Map<string, string>) => ({
      objectStore: () => ({
        get: (key: string) => {
          const r = {
            result: storeRef.get(key),
            onsuccess: null as ((e: unknown) => void) | null,
            onerror: null,
          };
          queueMicrotask(() => {
            if (r.onsuccess) r.onsuccess({ target: r });
          });
          return r;
        },
        put: (v: string, k: string) => storeRef.set(k, v),
        delete: (k: string) => storeRef.delete(k),
      }),
      oncomplete: null as ((e: unknown) => void) | null,
      onerror: null,
      onabort: null,
    });
    Object.defineProperty(globalThis, "indexedDB", {
      configurable: true,
      writable: true,
      value: {
        open: vi.fn(() => {
          const req = {
            result: {
              stores: new Map([["auth", store]]),
              transaction: () => makeTx(store),
              close: () => {},
            },
            onupgradeneeded: null,
            onsuccess: null as ((e: unknown) => void) | null,
            onerror: null,
            onblocked: null,
          };
          queueMicrotask(() => {
            if (req.onsuccess) req.onsuccess({ target: req });
          });
          return req;
        }),
      },
    });

    try {
      await sw.handleRefreshRequest({ source: targetClient });

      expect(fetchMock).toHaveBeenCalledTimes(1);
      // Direct reply: only the requesting client gets the message, no
      // fan-out to the BroadcastChannel or to every window client.
      expect(targetClient.postMessage).toHaveBeenCalledWith({
        type: "wyrdly:notifications-refreshed",
        payload: fakeFeed,
      });
    } finally {
      try {
        delete (globalThis as { indexedDB?: unknown }).indexedDB;
      } catch {
        /* ignore */
      }
    }
  });

  it("refreshNotifications returns null when no JWT is stored in IndexedDB", async () => {
    const sw = await loadSW();
    const fetchMock = vi.fn();

    const emptyStore = new Map<string, string>();
    const makeTx = (storeRef: Map<string, string>) => ({
      objectStore: () => ({
        get: (key: string) => {
          const r = {
            result: storeRef.get(key),
            onsuccess: null as ((e: unknown) => void) | null,
            onerror: null,
          };
          queueMicrotask(() => {
            if (r.onsuccess) r.onsuccess({ target: r });
          });
          return r;
        },
        put: (v: string, k: string) => storeRef.set(k, v),
        delete: (k: string) => storeRef.delete(k),
      }),
      oncomplete: null as ((e: unknown) => void) | null,
      onerror: null,
      onabort: null,
    });
    Object.defineProperty(globalThis, "indexedDB", {
      configurable: true,
      writable: true,
      value: {
        open: vi.fn(() => {
          const req = {
            result: {
              stores: new Map([["auth", emptyStore]]),
              transaction: () => makeTx(emptyStore),
              close: () => {},
            },
            onupgradeneeded: null,
            onsuccess: null as ((e: unknown) => void) | null,
            onerror: null,
            onblocked: null,
          };
          queueMicrotask(() => {
            if (req.onsuccess) req.onsuccess({ target: req });
          });
          return req;
        }),
      },
    });

    try {
      (globalThis as unknown as { fetch: unknown }).fetch = fetchMock;
      const result = await sw.refreshNotifications();
      expect(result).toBeNull();
      expect(fetchMock).not.toHaveBeenCalled();
    } finally {
      try {
        delete (globalThis as { indexedDB?: unknown }).indexedDB;
      } catch {
        /* ignore */
      }
    }
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
      expect(channelPostMessage).toHaveBeenCalledWith({
        type: "wyrdly:push-received",
        payload: { title: "Test", body: "Hello" },
      });
      // The SW now reuses a single BroadcastChannel for its lifetime instead
      // of creating-and-closing one per push. Verify the channel is not
      // closed, even after the previous 1s debounce window elapses.
      vi.useFakeTimers();
      vi.advanceTimersByTime(2000);
      vi.useRealTimers();
      expect(channelClose).not.toHaveBeenCalled();
      expect(postMessageClient).toHaveBeenCalledWith({
        type: "wyrdly:push-received",
        payload: { title: "Test", body: "Hello" },
      });
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
