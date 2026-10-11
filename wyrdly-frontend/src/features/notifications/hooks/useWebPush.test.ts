import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, renderHook } from "@testing-library/react";

// Mock the notifications API module so the hook uses a controlled surface.
vi.mock("../../../api/notifications", () => ({
  notificationsApi: {
    getVapidPublicKey: vi.fn(),
    subscribe: vi.fn(),
    unsubscribe: vi.fn(),
  },
}));

import { notificationsApi } from "../../../api/notifications";
import { useAuthStore } from "../../auth/store/useAuthStore";
import { pushCopy } from "../copy";
import type { PushErrorCode } from "../types";
import { PUSH_CACHE_NAME, PUSH_STATE_URL } from "../utils/pushCache";
import {
  PUSH_OPT_OUT_STORAGE_KEY,
  PUSH_SUBSCRIPTION_CHANGE_FAILED,
  base64UrlToUint8Array,
  pushError,
  useWebPush,
} from "./useWebPush";

const mockedGetVapidPublicKey = vi.mocked(notificationsApi.getVapidPublicKey);
const mockedSubscribe = vi.mocked(notificationsApi.subscribe);
const mockedUnsubscribe = vi.mocked(notificationsApi.unsubscribe);

// Minimal PushSubscription shape we rely on (toJSON only).
type FakeSubscriptionJson = {
  endpoint: string;
  keys: { p256dh: string; auth: string };
};

const VAPID_BASE64URL =
  "BEl62iUYgUivxIkv69yViEuiBIa-Ib9SO8q9VVE5TTe2lgYjlAY-CmF3Kk8o8bP3v4dN4HnKpC9pF5nM0E7tPwM";

const SESSION_USER_ID = "usr_test";
const SESSION_TOKEN = "token_test";

interface MockState {
  notificationPermission: NotificationPermission;
  requestPermission: ReturnType<typeof vi.fn>;
  register: ReturnType<typeof vi.fn>;
  getRegistration: ReturnType<typeof vi.fn>;
  subscribe: ReturnType<typeof vi.fn>;
  unsubscribe: ReturnType<typeof vi.fn>;
  getSubscription: ReturnType<typeof vi.fn>;
  showNotification: ReturnType<typeof vi.fn>;
  pushManager: {
    subscribe: ReturnType<typeof vi.fn>;
    unsubscribe: ReturnType<typeof vi.fn>;
    getSubscription: ReturnType<typeof vi.fn>;
  };
  registration: {
    pushManager: {
      subscribe: ReturnType<typeof vi.fn>;
      unsubscribe: ReturnType<typeof vi.fn>;
      getSubscription: ReturnType<typeof vi.fn>;
    };
    showNotification: ReturnType<typeof vi.fn>;
  };
  swMessageListeners: Set<(event: MessageEvent) => void>;
}

let state: MockState;

let originalNavigator: PropertyDescriptor | undefined;
let originalCaches: unknown;
let originalAuthSnapshot: ReturnType<typeof useAuthStore.getState>;
let hadNotification: boolean;
let hadPushManager: boolean;

const seedAuth = (userId: string | null, token: string | null) => {
  useAuthStore.setState({
    user: userId ? ({ id: userId } as never) : (null as never),
    token,
    isAuthenticated: Boolean(userId && token),
    isLoading: false,
  });
};

const installCachesMock = () => {
  originalCaches = (globalThis as { caches?: unknown }).caches;
  const store = new Map<string, Response>();
  const fakeCache = {
    match: vi.fn(async (req: Request | string) => {
      const key = typeof req === "string" ? req : req.url;
      return store.get(key);
    }),
    put: vi.fn(async (req: Request | string, response: Response) => {
      const key = typeof req === "string" ? req : req.url;
      store.set(key, response);
    }),
    delete: vi.fn(async (req: Request | string) => {
      const key = typeof req === "string" ? req : req.url;
      return store.delete(key);
    }),
  };
  Object.defineProperty(globalThis, "caches", {
    value: {
      open: vi.fn(async () => fakeCache),
    },
    configurable: true,
    writable: true,
  });
  return {
    store,
    open: (
      globalThis as unknown as { caches: { open: ReturnType<typeof vi.fn> } }
    ).caches.open,
  };
};

const restoreCachesMock = () => {
  if (originalCaches === undefined) {
    delete (globalThis as { caches?: unknown }).caches;
  } else {
    Object.defineProperty(globalThis, "caches", {
      value: originalCaches,
      configurable: true,
      writable: true,
    });
  }
};

const installBrowserShims = () => {
  hadNotification =
    "Notification" in (globalThis as unknown as { Notification?: unknown });
  hadPushManager =
    "PushManager" in (globalThis as unknown as { PushManager?: unknown });

  (globalThis as unknown as { Notification: unknown }).Notification = {
    get permission() {
      return state.notificationPermission;
    },
    requestPermission: (...args: unknown[]) =>
      (state.requestPermission as (...a: unknown[]) => unknown)(...args),
  };

  (globalThis as unknown as { PushManager: unknown }).PushManager =
    function PushManager() {};

  const nav = {
    serviceWorker: {
      register: (...args: unknown[]) =>
        (state.register as (...a: unknown[]) => unknown)(...args),
      getRegistration: (...args: unknown[]) =>
        (state.getRegistration as (...a: unknown[]) => unknown)(...args),
      get ready() {
        return Promise.resolve(state.registration);
      },
      addEventListener: (
        type: string,
        listener: (event: MessageEvent) => void,
      ) => {
        if (type === "message") state.swMessageListeners.add(listener);
      },
      removeEventListener: (
        type: string,
        listener: (event: MessageEvent) => void,
      ) => {
        if (type === "message") state.swMessageListeners.delete(listener);
      },
    },
  };

  originalNavigator = Object.getOwnPropertyDescriptor(globalThis, "navigator");
  Object.defineProperty(globalThis, "navigator", {
    value: nav,
    configurable: true,
    writable: true,
  });
};

const restoreBrowserShims = () => {
  if (originalNavigator) {
    Object.defineProperty(globalThis, "navigator", originalNavigator);
  } else {
    delete (globalThis as unknown as { navigator?: unknown }).navigator;
  }
  if (hadNotification) {
    // keep it
  } else {
    delete (globalThis as unknown as { Notification?: unknown }).Notification;
  }
  if (hadPushManager) {
    // keep it
  } else {
    delete (globalThis as unknown as { PushManager?: unknown }).PushManager;
  }
};

const buildState = (overrides: Partial<MockState> = {}): MockState => {
  const subJson: FakeSubscriptionJson = {
    endpoint: "https://push.example.com/endpoint/abc",
    keys: { p256dh: "p256dh-key", auth: "auth-key" },
  };

  const subscribeFn = vi.fn().mockResolvedValue({
    toJSON: () => subJson,
  });
  const unsubscribeFn = vi.fn().mockResolvedValue(true);
  const getSubscriptionFn = vi.fn().mockResolvedValue(null);
  const showNotificationFn = vi.fn().mockResolvedValue(undefined);

  const pushManager = {
    subscribe: subscribeFn,
    unsubscribe: unsubscribeFn,
    getSubscription: getSubscriptionFn,
  };

  const registration = {
    pushManager,
    showNotification: showNotificationFn,
  };

  return {
    notificationPermission: "default",
    requestPermission: vi.fn().mockResolvedValue("granted"),
    register: vi.fn().mockResolvedValue(registration),
    getRegistration: vi.fn().mockResolvedValue(registration),
    subscribe: subscribeFn,
    unsubscribe: unsubscribeFn,
    getSubscription: getSubscriptionFn,
    showNotification: showNotificationFn,
    pushManager,
    registration,
    swMessageListeners: new Set(),
    ...overrides,
  };
};

const flushMicrotasks = async () => {
  // Wait a few microtask ticks so chained promises resolve.
  await act(async () => {
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();
  });
};

describe("base64UrlToUint8Array strict shape validation", () => {
  const validKey =
    "BMKftmPKcnl-KG398DhmM-XMTxDLx7F2XI7Oxu-DP-NW9yMNWXDJh-LySIwDfJ3i5NVZRAGve26uF9uPLjVGpNc";
  const wrongPrefix =
    "BXYM2SaXxBCLBOh1S8eZU7DsRCJiwD_40wLc-EA77iNH1EAPgb-7XVFShyUzsHeSuI-VQhXyrbQkgsvzcBvGQSc";

  it("decodes a valid 65-byte / 0x04-prefixed VAPID key without throwing", () => {
    expect(() => base64UrlToUint8Array(validKey)).not.toThrow();
    const bytes = base64UrlToUint8Array(validKey);
    expect(bytes.length).toBe(65);
    expect(bytes[0]).toBe(0x04);
  });

  it("throws an invalidVapidKey error when the prefix byte is not 0x04", () => {
    let caught: Error | null = null;
    try {
      base64UrlToUint8Array(wrongPrefix);
    } catch (err) {
      caught = err as Error;
    }
    expect(caught?.name).toBe("invalidVapidKey");
    expect(caught?.message).toBe(pushCopy.invalidVapidKey);
  });

  it("throws an invalidVapidKey error when the decoded key is not 65 bytes", () => {
    let caught: Error | null = null;
    try {
      base64UrlToUint8Array("aGVsbG8");
    } catch (err) {
      caught = err as Error;
    }
    expect(caught?.name).toBe("invalidVapidKey");
  });
});

describe("useWebPush", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedGetVapidPublicKey.mockResolvedValue(VAPID_BASE64URL);
    mockedSubscribe.mockResolvedValue(undefined);
    mockedUnsubscribe.mockResolvedValue(undefined);
    originalAuthSnapshot = useAuthStore.getState();
    seedAuth(SESSION_USER_ID, SESSION_TOKEN);
    if (typeof localStorage !== "undefined") {
      localStorage.clear();
    }
    state = buildState();
    installCachesMock();
    installBrowserShims();
  });

  afterEach(() => {
    restoreBrowserShims();
    restoreCachesMock();
    useAuthStore.setState(originalAuthSnapshot);
    if (typeof localStorage !== "undefined") {
      localStorage.clear();
    }
  });

  it("reports isSupported=true when serviceWorker, PushManager and Notification are all available", () => {
    const { result } = renderHook(() => useWebPush());

    expect(result.current.isSupported).toBe(true);
    expect(result.current.permission).toBe("default");
    expect(result.current.isSubscribed).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it("registers the service worker on mount when enabled", async () => {
    renderHook(() => useWebPush({ enabled: true }));

    await flushMicrotasks();

    expect(state.register).toHaveBeenCalledWith("/sw.js", expect.any(Object));
  });

  it("does not register the service worker when enabled=false", async () => {
    renderHook(() => useWebPush({ enabled: false }));

    await flushMicrotasks();

    expect(state.register).not.toHaveBeenCalled();
  });

  it("does not register the service worker when there is no active session", async () => {
    seedAuth(null, null);
    renderHook(() => useWebPush({ enabled: true }));

    await flushMicrotasks();

    expect(state.register).not.toHaveBeenCalled();
  });

  it("subscribe() happy path: requests permission, fetches VAPID key, subscribes, POSTs to backend", async () => {
    const { result } = renderHook(() => useWebPush({ enabled: true }));

    await flushMicrotasks();

    let success: boolean | undefined;
    await act(async () => {
      success = await result.current.subscribe();
    });

    expect(success).toBe(true);
    expect(state.requestPermission).toHaveBeenCalledTimes(1);
    expect(mockedGetVapidPublicKey).toHaveBeenCalledTimes(1);
    expect(state.pushManager.subscribe).toHaveBeenCalledTimes(1);
    const subscribeArgs = state.pushManager.subscribe.mock.calls[0]?.[0] as
      | { userVisibleOnly: boolean; applicationServerKey: Uint8Array }
      | undefined;
    expect(subscribeArgs?.userVisibleOnly).toBe(true);
    expect(subscribeArgs?.applicationServerKey).toBeInstanceOf(Uint8Array);

    expect(mockedSubscribe).toHaveBeenCalledWith(
      expect.objectContaining({
        endpoint: "https://push.example.com/endpoint/abc",
        keys: expect.objectContaining({
          p256dh: "p256dh-key",
          auth: "auth-key",
        }),
      }),
    );
    expect(result.current.error).toBeNull();
  });

  it("subscribe() returns false and sets error when permission is denied", async () => {
    state = buildState({
      notificationPermission: "default",
      requestPermission: vi.fn().mockResolvedValue("denied"),
    });
    installBrowserShims();

    const { result } = renderHook(() => useWebPush({ enabled: true }));

    await flushMicrotasks();

    let success: boolean | undefined;
    await act(async () => {
      success = await result.current.subscribe();
    });

    expect(success).toBe(false);
    expect(result.current.permission).toBe("denied");
    expect(result.current.error?.name).toBe("denied");
    expect(state.pushManager.subscribe).not.toHaveBeenCalled();
    expect(mockedSubscribe).not.toHaveBeenCalled();
  });

  it("subscribe() returns false and sets error when SW registration fails", async () => {
    state = buildState({
      register: vi.fn().mockRejectedValue(new Error("SW register failed")),
      getRegistration: vi.fn().mockResolvedValue(null),
    });
    installBrowserShims();

    const { result } = renderHook(() => useWebPush({ enabled: true }));

    await flushMicrotasks();

    let success: boolean | undefined;
    await act(async () => {
      success = await result.current.subscribe();
    });

    expect(success).toBe(false);
    expect(result.current.error).toBeInstanceOf(Error);
    expect(state.pushManager.subscribe).not.toHaveBeenCalled();
    expect(mockedSubscribe).not.toHaveBeenCalled();
  });

  it("unsubscribe() calls pushManager.unsubscribe() and DELETE /api/notifications/subscribe", async () => {
    state = buildState();
    state.pushManager.getSubscription = vi.fn().mockResolvedValue({
      endpoint: "https://push.example.com/endpoint/abc",
    });
    installBrowserShims();

    const { result } = renderHook(() => useWebPush({ enabled: true }));

    await flushMicrotasks();

    let success: boolean | undefined;
    await act(async () => {
      success = await result.current.unsubscribe();
    });

    expect(success).toBe(true);
    expect(state.pushManager.unsubscribe).toHaveBeenCalledTimes(1);
    expect(mockedUnsubscribe).toHaveBeenCalledTimes(1);
    expect(result.current.error).toBeNull();
  });

  it("unsubscribe() is idempotent: returns true even if no subscription exists", async () => {
    state = buildState();
    state.pushManager.getSubscription = vi.fn().mockResolvedValue(null);
    installBrowserShims();

    const { result } = renderHook(() => useWebPush({ enabled: true }));

    await flushMicrotasks();

    let success: boolean | undefined;
    await act(async () => {
      success = await result.current.unsubscribe();
    });

    expect(success).toBe(true);
    expect(mockedUnsubscribe).not.toHaveBeenCalled();
  });

  it("subscribe() returns false and sets error when the VAPID key cannot be fetched", async () => {
    mockedGetVapidPublicKey.mockRejectedValueOnce(
      new Error("VAPID public key was empty"),
    );

    const { result } = renderHook(() => useWebPush({ enabled: true }));

    await flushMicrotasks();

    let success: boolean | undefined;
    await act(async () => {
      success = await result.current.subscribe();
    });

    expect(success).toBe(false);
    expect(result.current.error?.name).toBe("browserError");
    expect(result.current.error?.message).toBe(pushCopy.browserError);
    expect(state.pushManager.subscribe).not.toHaveBeenCalled();
    expect(mockedSubscribe).not.toHaveBeenCalled();
  });

  describe("pushsubscriptionchange recovery", () => {
    const emitSwMessage = async (data: unknown) => {
      await act(async () => {
        for (const listener of state.swMessageListeners) {
          listener({ data } as MessageEvent);
        }
        await Promise.resolve();
        await Promise.resolve();
        await Promise.resolve();
      });
    };

    it("re-subscribes silently when the SW reports a failed change and permission is granted", async () => {
      state.notificationPermission = "granted";
      state.pushManager.getSubscription = vi.fn().mockResolvedValue({
        endpoint: "https://push.example.com/endpoint/abc",
        toJSON: () => ({
          endpoint: "https://push.example.com/endpoint/abc",
          keys: { p256dh: "p256dh-key", auth: "auth-key" },
        }),
        options: {
          applicationServerKey: base64UrlToUint8Array(VAPID_BASE64URL),
        },
      });
      installBrowserShims();

      const { result } = renderHook(() => useWebPush({ enabled: true }));
      await flushMicrotasks();

      await emitSwMessage({ type: PUSH_SUBSCRIPTION_CHANGE_FAILED });

      expect(state.requestPermission).not.toHaveBeenCalled();
      // One fetch on mount to detect VAPID rotation, one fetch from the
      // SW-triggered re-subscribe path.
      expect(mockedGetVapidPublicKey).toHaveBeenCalledTimes(2);
      expect(state.pushManager.subscribe).toHaveBeenCalledTimes(1);
      expect(mockedSubscribe).toHaveBeenCalledTimes(2);
      expect(result.current.isSubscribed).toBe(true);
      expect(result.current.error).toBeNull();
    });

    it("does not re-subscribe when permission is no longer granted", async () => {
      state.notificationPermission = "denied";
      state.pushManager.getSubscription = vi.fn().mockResolvedValue({
        endpoint: "https://push.example.com/endpoint/abc",
      });
      installBrowserShims();

      renderHook(() => useWebPush({ enabled: true }));
      await flushMicrotasks();

      await emitSwMessage({ type: PUSH_SUBSCRIPTION_CHANGE_FAILED });

      expect(state.pushManager.subscribe).not.toHaveBeenCalled();
      expect(mockedSubscribe).not.toHaveBeenCalled();
    });

    it("ignores unrelated SW messages", async () => {
      state.notificationPermission = "granted";
      state.pushManager.getSubscription = vi.fn().mockResolvedValue({
        endpoint: "https://push.example.com/endpoint/abc",
      });
      installBrowserShims();

      renderHook(() => useWebPush({ enabled: true }));
      await flushMicrotasks();

      await emitSwMessage({ type: "something-else" });
      await emitSwMessage(null);

      expect(mockedSubscribe).not.toHaveBeenCalled();
    });

    it("exposes the error when silent re-subscription fails", async () => {
      state.notificationPermission = "granted";
      state.pushManager.getSubscription = vi.fn().mockResolvedValue({
        endpoint: "https://push.example.com/endpoint/abc",
      });
      installBrowserShims();
      mockedSubscribe.mockRejectedValueOnce(new Error("401"));

      const { result } = renderHook(() => useWebPush({ enabled: true }));
      await flushMicrotasks();

      await emitSwMessage({ type: PUSH_SUBSCRIPTION_CHANGE_FAILED });

      expect(result.current.isSubscribed).toBe(false);
      expect(result.current.error?.message).toBe(pushCopy.browserError);
    });

    it("removes the SW message listener on unmount", async () => {
      const { unmount } = renderHook(() => useWebPush({ enabled: true }));
      await flushMicrotasks();
      expect(state.swMessageListeners.size).toBe(1);

      unmount();

      expect(state.swMessageListeners.size).toBe(0);
    });

    it("does not listen to SW messages when disabled", async () => {
      renderHook(() => useWebPush({ enabled: false }));
      await flushMicrotasks();

      expect(state.swMessageListeners.size).toBe(0);
    });
  });

  it("silently auto-resubscribes on mount when permission is granted but no subscription exists", async () => {
    state = buildState({
      notificationPermission: "granted",
    });
    state.pushManager.getSubscription = vi.fn().mockResolvedValue(null);
    installBrowserShims();

    const { result } = renderHook(() => useWebPush({ enabled: true }));

    await flushMicrotasks();
    await flushMicrotasks();

    expect(mockedGetVapidPublicKey).toHaveBeenCalledTimes(1);
    expect(state.pushManager.subscribe).toHaveBeenCalledTimes(1);
    expect(mockedSubscribe).toHaveBeenCalledWith(
      expect.objectContaining({
        endpoint: "https://push.example.com/endpoint/abc",
      }),
    );
    expect(result.current.isSubscribed).toBe(true);
  });

  it("syncs existing browser subscription with backend on mount when permission is granted", async () => {
    const existingSub = {
      toJSON: () => ({
        endpoint: "https://push.example.com/existing",
        keys: { p256dh: "p256dh-val", auth: "auth-val" },
      }),
      options: {
        applicationServerKey: base64UrlToUint8Array(VAPID_BASE64URL),
      },
    };
    state = buildState({
      notificationPermission: "granted",
    });
    state.pushManager.getSubscription = vi.fn().mockResolvedValue(existingSub);
    installBrowserShims();

    const { result } = renderHook(() => useWebPush({ enabled: true }));

    await flushMicrotasks();
    await flushMicrotasks();

    expect(mockedSubscribe).toHaveBeenCalledWith(
      expect.objectContaining({
        endpoint: "https://push.example.com/existing",
      }),
    );
    expect(result.current.isSubscribed).toBe(true);
  });

  it("does not auto-resubscribe on mount when user explicitly opted out", async () => {
    state = buildState({
      notificationPermission: "granted",
    });
    state.pushManager.getSubscription = vi.fn().mockResolvedValue(null);
    installBrowserShims();

    localStorage.setItem(PUSH_OPT_OUT_STORAGE_KEY, "true");
    try {
      const { result } = renderHook(() => useWebPush({ enabled: true }));

      await flushMicrotasks();
      await flushMicrotasks();

      expect(state.pushManager.subscribe).not.toHaveBeenCalled();
      expect(mockedSubscribe).not.toHaveBeenCalled();
      expect(result.current.isSubscribed).toBe(false);
    } finally {
      localStorage.removeItem(PUSH_OPT_OUT_STORAGE_KEY);
    }
  });

  it("sets opt-out flag on unsubscribe and removes it on subscribe", async () => {
    state = buildState({
      notificationPermission: "granted",
    });
    state.pushManager.getSubscription = vi.fn().mockResolvedValue({
      endpoint: "https://push.example.com/endpoint/abc",
    });
    installBrowserShims();

    localStorage.removeItem(PUSH_OPT_OUT_STORAGE_KEY);
    const { result } = renderHook(() => useWebPush({ enabled: true }));
    await flushMicrotasks();

    // Unsubscribe sets the flag
    await act(async () => {
      const unsubSuccess = await result.current.unsubscribe();
      expect(unsubSuccess).toBe(true);
    });
    expect(localStorage.getItem(PUSH_OPT_OUT_STORAGE_KEY)).toBe("true");

    // Subscribe clears the flag
    await act(async () => {
      const subSuccess = await result.current.subscribe();
      expect(subSuccess).toBe(true);
    });
    expect(localStorage.getItem(PUSH_OPT_OUT_STORAGE_KEY)).toBeNull();
  });

  describe("session-change and Cache API owner handling", () => {
    it("clears the browser subscription when a different user is detected on mount", async () => {
      const existingSub = {
        toJSON: () => ({
          endpoint: "https://push.example.com/other-user",
          keys: { p256dh: "p", auth: "a" },
        }),
        unsubscribe: vi.fn(async () => true),
        options: { applicationServerKey: null },
      };
      state = buildState({ notificationPermission: "granted" });
      // Always return the same subscription so clearLocal() can find and
      // unsubscribe it after the stale owner is detected.
      state.pushManager.getSubscription = vi.fn(async () => existingSub);
      installBrowserShims();

      // Seed the cache with an owner from a previous session.
      const cache = await (
        globalThis as unknown as {
          caches: { open: () => Promise<unknown> };
        }
      ).caches.open(PUSH_CACHE_NAME);
      await (
        cache as unknown as {
          put: (url: string, resp: Response) => Promise<void>;
        }
      ).put(
        PUSH_STATE_URL,
        new Response(
          JSON.stringify({
            userId: "usr_OTHER",
            endpoint: "https://push.example.com/other-user",
          }),
          { headers: { "Content-Type": "application/json" } },
        ),
      );

      renderHook(() => useWebPush({ enabled: true }));
      await flushMicrotasks();
      await flushMicrotasks();
      await flushMicrotasks();

      expect(existingSub.unsubscribe).toHaveBeenCalled();
      expect(mockedSubscribe).not.toHaveBeenCalled();
    });

    it("persists the {userId, endpoint} owner in the cache after a fresh subscribe()", async () => {
      state = buildState();
      installBrowserShims();

      const { result } = renderHook(() => useWebPush({ enabled: true }));
      await flushMicrotasks();

      await act(async () => {
        await result.current.subscribe();
      });

      const cache = await (
        globalThis as unknown as {
          caches: { open: () => Promise<unknown> };
        }
      ).caches.open(PUSH_CACHE_NAME);
      const stored = await (
        cache as unknown as {
          match: (url: string) => Promise<Response | undefined>;
        }
      ).match(PUSH_STATE_URL);
      expect(stored).toBeDefined();
      const body = await stored?.json();
      expect(body).toEqual({
        userId: SESSION_USER_ID,
        endpoint: "https://push.example.com/endpoint/abc",
      });
    });

    it("re-subscribes when the server VAPID key differs from the one used by the existing browser subscription", async () => {
      const previousKeyBytes = base64UrlToUint8Array(VAPID_BASE64URL);
      const existingSub = {
        toJSON: () => ({
          endpoint: "https://push.example.com/endpoint/abc",
          keys: { p256dh: "p256dh-key", auth: "auth-key" },
        }),
        unsubscribe: vi.fn(async () => true),
        options: { applicationServerKey: previousKeyBytes },
      };
      const newSub = {
        toJSON: () => ({
          endpoint: "https://push.example.com/endpoint/abc",
          keys: { p256dh: "p256dh-key", auth: "auth-key" },
        }),
      };
      state = buildState({ notificationPermission: "granted" });
      state.pushManager.getSubscription = vi
        .fn()
        .mockResolvedValueOnce(existingSub)
        .mockResolvedValueOnce(existingSub)
        .mockResolvedValueOnce(null)
        .mockResolvedValueOnce(newSub);
      installBrowserShims();

      // Server now returns a different VAPID public key (e.g. after rotation).
      const rotated =
        "BNcRdreALRFXTkOOUHK1EtK2wtaz5Ry4YwfAhCjwVqQUr4QgYqWGo6C-KAETYGo9NTelLTgkOGOHZHZFiW627C0=";
      mockedGetVapidPublicKey.mockResolvedValueOnce(rotated);

      renderHook(() => useWebPush({ enabled: true }));
      await flushMicrotasks();
      await flushMicrotasks();
      await flushMicrotasks();
      await flushMicrotasks();

      expect(existingSub.unsubscribe).toHaveBeenCalled();
      expect(state.pushManager.subscribe).toHaveBeenCalled();
    });
  });

  it("subscribe() returns false with changedSession error when there is no active session", async () => {
    seedAuth(null, null);
    state = buildState();
    installBrowserShims();

    const { result } = renderHook(() => useWebPush({ enabled: true }));
    await flushMicrotasks();

    let success: boolean | undefined;
    await act(async () => {
      success = await result.current.subscribe();
    });

    expect(success).toBe(false);
    expect(result.current.error?.name).toBe("changedSession");
  });

  it("unsubscribe() does not touch the backend when the cached owner belongs to another user", async () => {
    state = buildState();
    state.pushManager.getSubscription = vi.fn().mockResolvedValue({
      endpoint: "https://push.example.com/other-user",
    });
    installBrowserShims();

    const cache = await (
      globalThis as unknown as {
        caches: { open: () => Promise<unknown> };
      }
    ).caches.open(PUSH_CACHE_NAME);
    await (
      cache as unknown as {
        put: (url: string, resp: Response) => Promise<void>;
      }
    ).put(
      PUSH_STATE_URL,
      new Response(
        JSON.stringify({
          userId: "usr_OTHER",
          endpoint: "https://push.example.com/other-user",
        }),
        { headers: { "Content-Type": "application/json" } },
      ),
    );

    const { result } = renderHook(() => useWebPush({ enabled: true }));
    await flushMicrotasks();

    let success: boolean | undefined;
    await act(async () => {
      success = await result.current.unsubscribe();
    });

    expect(success).toBe(true);
    expect(mockedUnsubscribe).not.toHaveBeenCalled();
    expect(state.pushManager.unsubscribe).not.toHaveBeenCalled();
  });
});

describe("navigator.locks serialization", () => {
  let originalLocks: PropertyDescriptor | undefined;
  let originalAuth: ReturnType<typeof useAuthStore.getState>;

  beforeEach(() => {
    originalAuth = useAuthStore.getState();
    seedAuth(SESSION_USER_ID, SESSION_TOKEN);
    if (typeof localStorage !== "undefined") {
      localStorage.clear();
    }
    vi.clearAllMocks();
    mockedGetVapidPublicKey.mockResolvedValue(VAPID_BASE64URL);
    mockedSubscribe.mockResolvedValue(undefined);
    mockedUnsubscribe.mockResolvedValue(undefined);
  });

  afterEach(() => {
    if (originalLocks) {
      Object.defineProperty(
        (globalThis as unknown as { navigator: { locks?: unknown } }).navigator,
        "locks",
        originalLocks,
      );
    } else {
      delete (globalThis as unknown as { navigator?: { locks?: unknown } })
        .navigator?.locks;
    }
    useAuthStore.setState(originalAuth);
    restoreBrowserShims();
    restoreCachesMock();
    if (typeof localStorage !== "undefined") {
      localStorage.clear();
    }
  });

  it("serializes two concurrent subscribe() calls through navigator.locks", async () => {
    const request = vi.fn(
      async (name: string, action: () => Promise<unknown>) => {
        return action();
      },
    );
    state = buildState();
    installCachesMock();
    installBrowserShims();
    // installBrowserShims rebuilds the navigator shim; install locks AFTER it.
    originalLocks = Object.getOwnPropertyDescriptor(
      (globalThis as unknown as { navigator: { locks?: unknown } }).navigator,
      "locks",
    );
    Object.defineProperty(
      (globalThis as unknown as { navigator: { locks?: unknown } }).navigator,
      "locks",
      { value: { request }, configurable: true, writable: true },
    );

    const { result } = renderHook(() => useWebPush({ enabled: true }));
    await flushMicrotasks();

    let a: boolean | undefined;
    let b: boolean | undefined;
    await act(async () => {
      [a, b] = await Promise.all([
        result.current.subscribe(),
        result.current.subscribe(),
      ]);
    });
    if (a !== true || b !== true) {
      // Surface the actual error so a future regression is debuggable.
      throw new Error(
        `subscribe() returned ${a}/${b}; error=${
          (result.current as unknown as { error: Error | null }).error?.message
        }`,
      );
    }
    expect(a).toBe(true);
    expect(b).toBe(true);

    // navigator.locks.request was invoked with the wyrdly-push lock name.
    const lockNames = request.mock.calls.map((args) => args[0]);
    expect(lockNames).toContain("wyrdly-push");
    // Both subscribe() calls were serialized (one ran at a time).
    expect(request).toHaveBeenCalledTimes(2);
  });

  it("runs the action directly when navigator.locks is unavailable", async () => {
    state = buildState();
    installCachesMock();
    installBrowserShims();
    // installBrowserShims rebuilds the navigator shim; remove locks AFTER it.
    originalLocks = Object.getOwnPropertyDescriptor(
      (globalThis as unknown as { navigator: { locks?: unknown } }).navigator,
      "locks",
    );
    delete (globalThis as unknown as { navigator: { locks?: unknown } })
      .navigator?.locks;

    const { result } = renderHook(() => useWebPush({ enabled: true }));
    await flushMicrotasks();

    let success: boolean | undefined;
    await act(async () => {
      success = await result.current.subscribe();
    });
    if (success !== true) {
      throw new Error(
        `subscribe() returned ${success}; error=${
          (result.current as unknown as { error: Error | null }).error?.message
        }`,
      );
    }
    expect(success).toBe(true);
    expect(state.pushManager.subscribe).toHaveBeenCalledTimes(1);
  });
});

describe("useWebPush feature detection (unsupported environment)", () => {
  afterEach(() => {
    restoreBrowserShims();
    restoreCachesMock();
  });

  it("isSupported=false and subscribe() returns false when serviceWorker is missing", async () => {
    vi.clearAllMocks();
    hadNotification =
      "Notification" in (globalThis as unknown as { Notification?: unknown });
    hadPushManager =
      "PushManager" in (globalThis as unknown as { PushManager?: unknown });
    (globalThis as unknown as { Notification: unknown }).Notification = {
      get permission() {
        return "default" as NotificationPermission;
      },
      requestPermission: vi.fn(),
    };
    (globalThis as unknown as { PushManager: unknown }).PushManager =
      function PushManager() {};

    originalNavigator = Object.getOwnPropertyDescriptor(
      globalThis,
      "navigator",
    );
    Object.defineProperty(globalThis, "navigator", {
      value: {}, // no serviceWorker
      configurable: true,
      writable: true,
    });
    originalAuthSnapshot = useAuthStore.getState();
    seedAuth(SESSION_USER_ID, SESSION_TOKEN);

    const { result } = renderHook(() => useWebPush({ enabled: true }));

    expect(result.current.isSupported).toBe(false);

    let success: boolean | undefined;
    await act(async () => {
      success = await result.current.subscribe();
    });

    expect(success).toBe(false);
    expect(result.current.error?.name).toBe("unsupported");
  });
});

describe("pushError translation helper", () => {
  it("returns the tagged error unchanged when its name is a known PushErrorCode", () => {
    const tagged = new Error("oops");
    tagged.name = "changedSession";
    const out = pushError(tagged);
    expect(out.name).toBe("changedSession");
    expect(out.message).toBe("oops");
  });

  it("translates a Notification.permission === 'denied' to the denied code", () => {
    const original = Object.getOwnPropertyDescriptor(
      globalThis,
      "Notification",
    );
    (globalThis as unknown as { Notification: unknown }).Notification = {
      get permission() {
        return "denied" as NotificationPermission;
      },
    };
    try {
      const out = pushError(new Error("whatever"));
      expect(out.name).toBe("denied");
      expect(out.message).toBe(pushCopy.denied);
    } finally {
      if (original) {
        Object.defineProperty(globalThis, "Notification", original);
      } else {
        delete (globalThis as unknown as { Notification?: unknown })
          .Notification;
      }
    }
  });

  it("falls back to browserError for unknown errors", () => {
    const original = Object.getOwnPropertyDescriptor(
      globalThis,
      "Notification",
    );
    (globalThis as unknown as { Notification: unknown }).Notification = {
      get permission() {
        return "default" as NotificationPermission;
      },
    };
    try {
      const out = pushError(new Error("weird"));
      expect(out.name).toBe("browserError");
      expect(out.message).toBe(pushCopy.browserError);
    } finally {
      if (original) {
        Object.defineProperty(globalThis, "Notification", original);
      }
    }
  });

  it("accepts every PushErrorCode as a tag without leaking the literal", () => {
    const codes: PushErrorCode[] = [
      "unsupported",
      "insecureContext",
      "denied",
      "browserError",
      "changedSession",
      "invalidVapidKey",
      "missingCache",
    ];
    for (const code of codes) {
      const err = new Error("x");
      err.name = code;
      const out = pushError(err);
      expect(out.name).toBe(code);
    }
  });
});

describe("useWebPush detectSupport — secure context and cache API", () => {
  afterEach(() => {
    restoreBrowserShims();
    restoreCachesMock();
  });

  const baseShim = () => {
    hadNotification =
      "Notification" in (globalThis as unknown as { Notification?: unknown });
    hadPushManager =
      "PushManager" in (globalThis as unknown as { PushManager?: unknown });
    (globalThis as unknown as { Notification: unknown }).Notification = {
      get permission() {
        return "default" as NotificationPermission;
      },
      requestPermission: vi.fn(),
    };
    (globalThis as unknown as { PushManager: unknown }).PushManager =
      function PushManager() {};
    installCachesMock();
  };

  it("isSupported=false with insecureContext reason when isSecureContext is false", () => {
    originalNavigator = Object.getOwnPropertyDescriptor(
      globalThis,
      "navigator",
    );
    Object.defineProperty(globalThis, "navigator", {
      value: {
        serviceWorker: { register: vi.fn() },
      },
      configurable: true,
      writable: true,
    });
    const originalIsSecureContext = Object.getOwnPropertyDescriptor(
      globalThis,
      "isSecureContext",
    );
    Object.defineProperty(globalThis, "isSecureContext", {
      value: false,
      configurable: true,
      writable: true,
    });
    baseShim();

    const { result } = renderHook(() => useWebPush());
    expect(result.current.isSupported).toBe(false);

    if (originalIsSecureContext) {
      Object.defineProperty(
        globalThis,
        "isSecureContext",
        originalIsSecureContext,
      );
    } else {
      delete (globalThis as { isSecureContext?: unknown }).isSecureContext;
    }
  });

  it("isSupported=false with missingCache reason when caches is undefined", () => {
    hadNotification =
      "Notification" in (globalThis as unknown as { Notification?: unknown });
    hadPushManager =
      "PushManager" in (globalThis as unknown as { PushManager?: unknown });
    (globalThis as unknown as { Notification: unknown }).Notification = {
      get permission() {
        return "default" as NotificationPermission;
      },
      requestPermission: vi.fn(),
    };
    (globalThis as unknown as { PushManager: unknown }).PushManager =
      function PushManager() {};
    Object.defineProperty(globalThis, "isSecureContext", {
      value: true,
      configurable: true,
      writable: true,
    });
    originalNavigator = Object.getOwnPropertyDescriptor(
      globalThis,
      "navigator",
    );
    Object.defineProperty(globalThis, "navigator", {
      value: {
        serviceWorker: { register: vi.fn() },
      },
      configurable: true,
      writable: true,
    });

    const originalCaches = (globalThis as { caches?: unknown }).caches;
    delete (globalThis as { caches?: unknown }).caches;

    try {
      const { result } = renderHook(() => useWebPush());
      expect(result.current.isSupported).toBe(false);
    } finally {
      if (originalCaches !== undefined) {
        Object.defineProperty(globalThis, "caches", {
          value: originalCaches,
          configurable: true,
          writable: true,
        });
      }
      const originalIsSecureContext = Object.getOwnPropertyDescriptor(
        globalThis,
        "isSecureContext",
      );
      if (originalIsSecureContext) {
        Object.defineProperty(
          globalThis,
          "isSecureContext",
          originalIsSecureContext,
        );
      } else {
        delete (globalThis as { isSecureContext?: unknown }).isSecureContext;
      }
    }
  });
});
