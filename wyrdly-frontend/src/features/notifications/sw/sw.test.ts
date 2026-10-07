/**
 * Exercises the pure handlers exposed by `public/sw.js` against a mocked
 * `self`. The SW file is loaded once per test; its module-level `addEventListener`
 * calls are guarded by `isServiceWorkerContext()` so they stay inert under
 * Vitest's jsdom environment.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

type SW = {
  handlePush: (event: unknown) => Promise<void>;
  handleNotificationClick: (event: unknown) => Promise<void>;
  handleSubscriptionChange: (event: unknown) => Promise<void>;
  handleInstall: (event: unknown) => void;
  handleActivate: (event: unknown) => void;
  urlBase64ToUint8Array: (input: string) => Uint8Array;
};

let cachedSW: SW | null = null;

const loadSW = async (): Promise<SW> => {
  if (cachedSW) {
    return cachedSW;
  }
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
    const [title, options] = showNotification.mock.calls[0] as [string, Record<string, unknown>];
    expect(title).toBe("Nuevo seguidor");
    expect(options.body).toBe("Ana comenzó a seguirte");
    expect(options.icon).toBe("/icons/x.png");
    expect(options.badge).toBe("/icons/b.png");
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
    const [, options] = showNotification.mock.calls[0] as [string, Record<string, unknown>];
    expect(options.body).toBe("raw text");
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
        matchAll: vi.fn().mockResolvedValue([
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
    const newSub = { toJSON: () => ({ endpoint: "https://push.example.com/new" }) };
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
    const [url, init] = fetchMock.mock.calls[1] as [string, Record<string, unknown>];
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
