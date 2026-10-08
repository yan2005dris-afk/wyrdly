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
import { PUSH_SUBSCRIPTION_CHANGE_FAILED, useWebPush } from "./useWebPush";

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
let hadNotification: boolean;
let hadPushManager: boolean;

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
  // Wait two microtask ticks so chained promises resolve.
  await act(async () => {
    await Promise.resolve();
    await Promise.resolve();
  });
};

describe("useWebPush", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedGetVapidPublicKey.mockResolvedValue(VAPID_BASE64URL);
    mockedSubscribe.mockResolvedValue(undefined);
    mockedUnsubscribe.mockResolvedValue(undefined);
    state = buildState();
    installBrowserShims();
  });

  afterEach(() => {
    restoreBrowserShims();
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

    const { result } = renderHook(() => useWebPush({ enabled: true }));

    await flushMicrotasks();

    let success: boolean | undefined;
    await act(async () => {
      success = await result.current.subscribe();
    });

    expect(success).toBe(false);
    expect(result.current.permission).toBe("denied");
    expect(result.current.error).toBeInstanceOf(Error);
    expect(state.pushManager.subscribe).not.toHaveBeenCalled();
    expect(mockedSubscribe).not.toHaveBeenCalled();
  });

  it("subscribe() returns false and sets error when SW registration fails", async () => {
    state = buildState({
      register: vi.fn().mockRejectedValue(new Error("SW register failed")),
      getRegistration: vi.fn().mockResolvedValue(null),
    });

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
    expect(result.current.error?.message).toBe("VAPID public key was empty");
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
      const { result } = renderHook(() => useWebPush({ enabled: true }));
      await flushMicrotasks();

      await emitSwMessage({ type: PUSH_SUBSCRIPTION_CHANGE_FAILED });

      expect(state.requestPermission).not.toHaveBeenCalled();
      expect(mockedGetVapidPublicKey).toHaveBeenCalledTimes(1);
      expect(state.pushManager.subscribe).toHaveBeenCalledTimes(1);
      expect(mockedSubscribe).toHaveBeenCalledTimes(1);
      expect(result.current.isSubscribed).toBe(true);
      expect(result.current.error).toBeNull();
    });

    it("does not re-subscribe when permission is no longer granted", async () => {
      state.notificationPermission = "denied";
      renderHook(() => useWebPush({ enabled: true }));
      await flushMicrotasks();

      await emitSwMessage({ type: PUSH_SUBSCRIPTION_CHANGE_FAILED });

      expect(state.pushManager.subscribe).not.toHaveBeenCalled();
      expect(mockedSubscribe).not.toHaveBeenCalled();
    });

    it("ignores unrelated SW messages", async () => {
      state.notificationPermission = "granted";
      renderHook(() => useWebPush({ enabled: true }));
      await flushMicrotasks();

      await emitSwMessage({ type: "something-else" });
      await emitSwMessage(null);

      expect(mockedSubscribe).not.toHaveBeenCalled();
    });

    it("exposes the error when silent re-subscription fails", async () => {
      state.notificationPermission = "granted";
      mockedSubscribe.mockRejectedValueOnce(new Error("401"));
      const { result } = renderHook(() => useWebPush({ enabled: true }));
      await flushMicrotasks();

      await emitSwMessage({ type: PUSH_SUBSCRIPTION_CHANGE_FAILED });

      expect(result.current.isSubscribed).toBe(false);
      expect(result.current.error?.message).toBe("401");
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
});

describe("useWebPush feature detection (unsupported environment)", () => {
  afterEach(() => {
    restoreBrowserShims();
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

    const { result } = renderHook(() => useWebPush({ enabled: true }));

    expect(result.current.isSupported).toBe(false);

    let success: boolean | undefined;
    await act(async () => {
      success = await result.current.subscribe();
    });

    expect(success).toBe(false);
    expect(result.current.error).toBeInstanceOf(Error);
  });
});
