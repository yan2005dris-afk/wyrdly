import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, renderHook, screen } from "@testing-library/react";
import { useAuthStore } from "../../../auth/store/useAuthStore";
import { pushCopy } from "../../../notifications/copy";

// Mock the notifications API module so the underlying useWebPush hook uses a
// controlled surface, mirroring the setup in notifications/hooks/useWebPush.test.ts.
vi.mock("../../../../api/notifications", () => ({
  notificationsApi: {
    getVapidPublicKey: vi.fn(),
    subscribe: vi.fn(),
    unsubscribe: vi.fn(),
  },
}));

import { notificationsApi } from "../../../../api/notifications";
import { useWebPush } from "../../../notifications/hooks/useWebPush";
import { WebPushSettingsCard } from "./WebPushSettingsCard";

const mockedGetVapidPublicKey = vi.mocked(notificationsApi.getVapidPublicKey);
const mockedSubscribe = vi.mocked(notificationsApi.subscribe);
const mockedUnsubscribe = vi.mocked(notificationsApi.unsubscribe);

const VAPID_BASE64URL =
  "BEl62iUYgUivxIkv69yViEuiBIa-Ib9SO8q9VVE5TTe2lgYjlAY-CmF3Kk8o8bP3v4dN4HnKpC9pF5nM0E7tPwM";

interface MockState {
  notificationPermission: NotificationPermission;
  requestPermission: ReturnType<typeof vi.fn>;
  register: ReturnType<typeof vi.fn>;
  subscribe: ReturnType<typeof vi.fn>;
  unsubscribe: ReturnType<typeof vi.fn>;
  getSubscription: ReturnType<typeof vi.fn>;
  pushManager: {
    subscribe: ReturnType<typeof vi.fn>;
    unsubscribe: ReturnType<typeof vi.fn>;
    getSubscription: ReturnType<typeof vi.fn>;
  };
  registration: unknown;
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

const installShims = () => {
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
        (state.register as (...a: unknown[]) => unknown)(...args),
      get ready() {
        return Promise.resolve(state.registration);
      },
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
    },
  };
  originalNavigator = Object.getOwnPropertyDescriptor(globalThis, "navigator");
  Object.defineProperty(globalThis, "navigator", {
    value: nav,
    configurable: true,
    writable: true,
  });
};

const restoreShims = () => {
  if (originalNavigator) {
    Object.defineProperty(globalThis, "navigator", originalNavigator);
  } else {
    delete (globalThis as unknown as { navigator?: unknown }).navigator;
  }
  if (!hadNotification) {
    delete (globalThis as unknown as { Notification?: unknown }).Notification;
  }
  if (!hadPushManager) {
    delete (globalThis as unknown as { PushManager?: unknown }).PushManager;
  }
};

const installCaches = () => {
  originalCaches = (globalThis as { caches?: unknown }).caches;
  const store = new Map<string, Response>();
  Object.defineProperty(globalThis, "caches", {
    value: {
      open: vi.fn(async () => ({
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
      })),
    },
    configurable: true,
    writable: true,
  });
};

const restoreCaches = () => {
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

const buildState = (overrides: Partial<MockState> = {}): MockState => {
  const subscribeFn = vi.fn().mockResolvedValue({
    toJSON: () => ({
      endpoint: "https://push.example.com/endpoint/abc",
      keys: { p256dh: "p256dh-key", auth: "auth-key" },
    }),
  });
  const unsubscribeFn = vi.fn().mockResolvedValue(true);
  const getSubscriptionFn = vi.fn().mockResolvedValue(null);
  const registration = {
    pushManager: {
      subscribe: subscribeFn,
      unsubscribe: unsubscribeFn,
      getSubscription: getSubscriptionFn,
    },
    showNotification: vi.fn(),
  };
  return {
    notificationPermission: "default",
    requestPermission: vi.fn().mockResolvedValue("granted"),
    register: vi.fn().mockResolvedValue(registration),
    subscribe: subscribeFn,
    unsubscribe: unsubscribeFn,
    getSubscription: getSubscriptionFn,
    pushManager: {
      subscribe: subscribeFn,
      unsubscribe: unsubscribeFn,
      getSubscription: getSubscriptionFn,
    },
    registration,
    ...overrides,
  };
};

const flushMicrotasks = async () => {
  await act(async () => {
    await Promise.resolve();
    await Promise.resolve();
  });
};

describe("WebPushSettingsCard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedGetVapidPublicKey.mockResolvedValue(VAPID_BASE64URL);
    mockedSubscribe.mockResolvedValue(undefined);
    mockedUnsubscribe.mockResolvedValue(undefined);
    originalAuthSnapshot = useAuthStore.getState();
    seedAuth("usr_test", "token_test");
    if (typeof localStorage !== "undefined") {
      localStorage.clear();
    }
    state = buildState();
    installCaches();
    installShims();
  });

  afterEach(() => {
    restoreShims();
    restoreCaches();
    useAuthStore.setState(originalAuthSnapshot);
    if (typeof localStorage !== "undefined") {
      localStorage.clear();
    }
  });

  it("renders with disabled state and Habilitar CTA when nothing is subscribed yet", () => {
    render(<WebPushSettingsCard />);
    const card = screen.getByTestId("webpush-settings-card");
    expect(card).toHaveAttribute("data-state", "disabled");
    expect(screen.getByTestId("webpush-settings-badge")).toHaveTextContent(
      "Deshabilitado",
    );
    expect(
      screen.getByRole("button", { name: "Habilitar" }),
    ).toBeInTheDocument();
  });

  it("shows Habilitado badge and Deshabilitar CTA after enabling", async () => {
    render(<WebPushSettingsCard />);
    await flushMicrotasks();

    await act(async () => {
      screen.getByRole("button", { name: "Habilitar" }).click();
    });
    await flushMicrotasks();
    await flushMicrotasks();

    const card = screen.getByTestId("webpush-settings-card");
    expect(card).toHaveAttribute("data-state", "enabled");
    expect(screen.getByTestId("webpush-settings-badge")).toHaveTextContent(
      "Habilitado",
    );
    expect(
      screen.getByRole("button", { name: "Deshabilitar" }),
    ).toBeInTheDocument();
  });

  it("calls notificationsApi.unsubscribe when disabling an active subscription", async () => {
    const existingSub = {
      endpoint: "https://push.example.com/endpoint/abc",
      toJSON: () => ({
        endpoint: "https://push.example.com/endpoint/abc",
        keys: { p256dh: "p256dh-key", auth: "auth-key" },
      }),
      options: { applicationServerKey: null },
    };
    const getSubscriptionFn = vi.fn().mockResolvedValue(existingSub);
    const subscribeFn = vi.fn().mockResolvedValue({
      toJSON: () => ({
        endpoint: "https://push.example.com/endpoint/abc",
        keys: { p256dh: "p256dh-key", auth: "auth-key" },
      }),
    });
    const registration = {
      pushManager: {
        subscribe: subscribeFn,
        unsubscribe: vi.fn().mockResolvedValue(true),
        getSubscription: getSubscriptionFn,
      },
      showNotification: vi.fn(),
    };
    state = buildState({
      notificationPermission: "granted",
      pushManager: registration.pushManager,
      getSubscription: getSubscriptionFn,
      registration,
    });
    installShims();

    render(<WebPushSettingsCard />);
    await flushMicrotasks();
    await flushMicrotasks();

    // The hook auto-detects the existing subscription and reports enabled.
    expect(screen.getByTestId("webpush-settings-card")).toHaveAttribute(
      "data-state",
      "enabled",
    );

    await act(async () => {
      screen.getByRole("button", { name: "Deshabilitar" }).click();
    });
    await flushMicrotasks();

    expect(mockedUnsubscribe).toHaveBeenCalled();
  });

  it("renders Bloqueado badge and disables the toggle when permission is denied", () => {
    state = buildState({
      notificationPermission: "denied",
      requestPermission: vi.fn().mockResolvedValue("denied"),
    });
    installShims();

    render(<WebPushSettingsCard />);
    const card = screen.getByTestId("webpush-settings-card");
    expect(card).toHaveAttribute("data-state", "disabled");
    expect(screen.getByTestId("webpush-settings-badge")).toHaveTextContent(
      "Bloqueado por el navegador",
    );
    expect(screen.getByRole("button", { name: "Habilitar" })).toBeDisabled();
    expect(
      screen.getByText(pushCopy.denied, { exact: false }),
    ).toBeInTheDocument();
  });

  it("renders No disponible badge when the browser does not expose the required APIs", () => {
    hadNotification =
      "Notification" in (globalThis as unknown as { Notification?: unknown });
    hadPushManager =
      "PushManager" in (globalThis as unknown as { PushManager?: unknown });
    delete (globalThis as unknown as { Notification?: unknown }).Notification;
    delete (globalThis as unknown as { PushManager?: unknown }).PushManager;
    originalNavigator = Object.getOwnPropertyDescriptor(
      globalThis,
      "navigator",
    );
    Object.defineProperty(globalThis, "navigator", {
      value: {},
      configurable: true,
      writable: true,
    });

    render(<WebPushSettingsCard />);
    const card = screen.getByTestId("webpush-settings-card");
    expect(card).toHaveAttribute("data-state", "disabled");
    expect(screen.getByTestId("webpush-settings-badge")).toHaveTextContent(
      "No disponible",
    );
    expect(screen.getByRole("button", { name: "Habilitar" })).toBeDisabled();
  });

  describe("bug reproduction: changedSession error must not show on first mount", () => {
    it("does NOT render the error box on first mount with a valid session", () => {
      render(<WebPushSettingsCard />);
      expect(screen.queryByTestId("webpush-settings-error")).toBeNull();
      expect(
        screen.queryByText(pushCopy.changedSession, { exact: false }),
      ).toBeNull();
    });

    it("shows the changedSession error AFTER a Habilitar click that fails because the session was cleared mid-flight", async () => {
      state = buildState();
      installShims();
      const { result } = renderHook(() => useWebPush({ enabled: true }));
      // simulate logout between mount and click
      await act(async () => {
        seedAuth(null, null);
      });
      let success: boolean | undefined;
      await act(async () => {
        success = await result.current.subscribe();
      });
      expect(success).toBe(false);
      expect(result.current.error?.name).toBe("changedSession");
    });

    it("BUG: error from a previous failed Habilitar persists after the user logs back in", async () => {
      state = buildState();
      installShims();

      render(<WebPushSettingsCard />);
      await flushMicrotasks();

      // Step 1: simulate logout (token expired between mount and click)
      await act(async () => {
        seedAuth(null, null);
      });

      // Step 2: user clicks Habilitar — fails with changedSession
      await act(async () => {
        screen.getByRole("button", { name: "Habilitar" }).click();
      });
      await flushMicrotasks();

      // The error is now visible (this is expected)
      expect(screen.getByTestId("webpush-settings-error")).toBeInTheDocument();
      expect(
        screen.getByText(pushCopy.changedSession, { exact: false }),
      ).toBeInTheDocument();

      // Step 3: user logs back in
      await act(async () => {
        seedAuth("usr_test", "token_test");
      });
      await flushMicrotasks();

      // BUG: even with a valid session, the stale error is still shown
      expect(
        screen.queryByText(pushCopy.changedSession, { exact: false }),
      ).toBeNull();
    });
  });
});
