import { useCallback, useEffect, useRef, useState } from "react";
import { notificationsApi } from "../../../api/notifications";
import type { PushErrorCode, PushPermissionStatus } from "../types";
import {
  clearLocal,
  clearOwner,
  getOwner,
  setOwner,
  type PushOwner,
} from "../utils/pushCache";
import {
  assertSession,
  getActiveSession,
  makePushError,
} from "../utils/pushSession";

/**
 * Message type posted by `public/sw.js` when it could not re-subscribe on
 * `pushsubscriptionchange` (the SW has no access token). Must stay in sync
 * with `PUSH_CHANGE_FAILED` in the service worker.
 */
export const PUSH_SUBSCRIPTION_CHANGE_FAILED =
  "push-subscription-change-failed";

export interface UseWebPushOptions {
  readonly enabled?: boolean;
}

export interface UseWebPushResult {
  readonly isSupported: boolean;
  readonly permission: PushPermissionStatus;
  readonly isSubscribed: boolean;
  readonly subscribe: () => Promise<boolean>;
  readonly unsubscribe: () => Promise<boolean>;
  readonly error: Error | null;
}

const SW_SCOPE_OPTION: RegistrationOptions = { scope: "/" };
export const PUSH_OPT_OUT_STORAGE_KEY = "wyrdly:push:opt-out";

/**
 * Decodes a base64url VAPID public key into a `Uint8Array` suitable for
 * `PushManager.subscribe({ applicationServerKey })`. Padding, `-` and `_` are
 * normalised back to standard base64 before `atob`.
 *
 * <p>Throws a `PushErrorCode.invalidVapidKey`-tagged error when the
 * resulting bytes do not match the ANSI X9.62 uncompressed EC point shape:
 * exactly 65 bytes starting with the `0x04` prefix. The browser would
 * otherwise reject a malformed key with a generic `InvalidAccessError` that
 * gives the user no actionable feedback.
 */
export const base64UrlToUint8Array = (input: string): Uint8Array => {
  const trimmed = input.trim();
  const padding = "=".repeat((4 - (trimmed.length % 4)) % 4);
  const base64 = (trimmed + padding).replace(/-/g, "+").replace(/_/g, "/");
  const raw = atob(base64);
  const out = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i += 1) {
    out[i] = raw.charCodeAt(i);
  }
  if (out.length !== 65 || out[0] !== 0x04) {
    throw makePushError("invalidVapidKey");
  }
  return out;
};

export interface DetectSupportResult {
  readonly supported: boolean;
  readonly reason?: PushErrorCode;
}

const detectSupport = (): DetectSupportResult => {
  if (typeof window === "undefined" || typeof navigator === "undefined") {
    return { supported: false, reason: "unsupported" };
  }
  if (
    typeof window.isSecureContext !== "undefined" &&
    window.isSecureContext !== true
  ) {
    return { supported: false, reason: "insecureContext" };
  }
  const nav = navigator as Navigator & {
    serviceWorker?: ServiceWorkerContainer;
  };
  if (!nav.serviceWorker) {
    return { supported: false, reason: "unsupported" };
  }
  const win = window as Window & {
    PushManager?: unknown;
  };
  if (typeof win.PushManager === "undefined") {
    return { supported: false, reason: "unsupported" };
  }
  if (typeof window.Notification === "undefined") {
    return { supported: false, reason: "unsupported" };
  }
  if (typeof caches === "undefined") {
    return { supported: false, reason: "missingCache" };
  }
  return { supported: true };
};

const readCurrentPermission = (): PushPermissionStatus => {
  if (
    typeof window === "undefined" ||
    typeof window.Notification === "undefined"
  ) {
    return "default";
  }
  return window.Notification.permission as PushPermissionStatus;
};

/**
 * Returns the {userId, token} pair that should be passed to backend mutation
 * endpoints. Reads synchronously from the Zustand auth store so the hook
 * can stay decoupled from React's render lifecycle.
 */
const readActiveCredentials = (): { userId: string; token: string } | null => {
  const session = getActiveSession();
  if (!session) return null;
  return { userId: session.userId, token: session.token };
};

const sameVapidKey = (
  a: ArrayBuffer | Uint8Array | null,
  b: Uint8Array,
): boolean => {
  if (!a) return false;
  const bytes = a instanceof Uint8Array ? a : new Uint8Array(a);
  if (bytes.length !== b.length) return false;
  for (let i = 0; i < bytes.length; i += 1) {
    if (bytes[i] !== b[i]) return false;
  }
  return true;
};

/**
 * Serializes enable/disable across tabs so two concurrent calls don't race
 * the browser-side subscription state. Falls back to running the action
 * directly when `navigator.locks` is unavailable (Safari < 15.4, embedded
 * WebViews).
 */
async function withPushLock<T>(action: () => Promise<T>): Promise<T> {
  const nav = (typeof navigator !== "undefined" ? navigator : undefined) as
    | (Navigator & {
        locks?: {
          request: (
            name: string,
            cb: () => Promise<T>,
          ) => Promise<T>;
        };
      })
    | undefined;
  if (!nav?.locks?.request) {
    return action();
  }
  return nav.locks.request("wyrdly-push", action);
}

/**
 * Creates a browser push subscription bound to the server's VAPID key and
 * registers it with the backend. The supplied credentials are re-checked
 * against the active session after the (async) `pushManager.subscribe` call
 * to defeat a fast login/logout race.
 */
const createPushSubscription = async (
  registration: ServiceWorkerRegistration,
  credentials: { userId: string; token: string },
): Promise<void> => {
  assertSession(credentials.userId, credentials.token);
  const vapidKey = await notificationsApi.getVapidPublicKey();
  assertSession(credentials.userId, credentials.token);
  const subscription = await registration.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey: base64UrlToUint8Array(vapidKey) as BufferSource,
  });
  assertSession(credentials.userId, credentials.token);
  const json = subscription.toJSON();
  await notificationsApi.subscribe(json);
  assertSession(credentials.userId, credentials.token);
  const owner: PushOwner = {
    userId: credentials.userId,
    endpoint: json.endpoint ?? subscription.endpoint,
  };
  await setOwner(owner);
};

/**
 * Maps any error surfaced by the Web Push flow to a `PushErrorCode`-tagged
 * Error that the UI can translate via `pushCopy`. Falls back to
 * `browserError` for everything that isn't already a tagged Push error or a
 * notification-permission denial.
 */
export const pushError = (error: unknown): Error => {
  if (error instanceof Error && (error as Error & { name?: string }).name) {
    const tagged = error as Error & { name: string };
    const known: PushErrorCode[] = [
      "unsupported",
      "insecureContext",
      "denied",
      "browserError",
      "changedSession",
      "invalidVapidKey",
      "missingCache",
    ];
    if (known.includes(tagged.name as PushErrorCode)) {
      return tagged;
    }
  }
  if (
    typeof window !== "undefined" &&
    typeof window.Notification !== "undefined" &&
    window.Notification.permission === "denied"
  ) {
    return makePushError("denied");
  }
  return makePushError("browserError");
};

/**
 * Manages the Web Push subscription lifecycle for the current browser.
 *
 * Responsibilities:
 *  - Registers `/sw.js` once when `enabled` is true.
 *  - Requests browser permission, fetches the VAPID public key, subscribes via
 *    `PushManager.subscribe`, and POSTs the resulting subscription to the
 *    backend.
 *  - Persists the {userId, endpoint} owner pair in the Cache API so a
 *    subsequent mount can detect a session change and tear down a stale
 *    subscription before creating a fresh one.
 *  - Detects VAPID key rotation by comparing the current server key
 *    against the one the existing browser subscription was created with.
 *  - Recovers from `pushsubscriptionchange`: when the SW reports it could not
 *    re-subscribe, silently re-subscribes from the page (which holds the
 *    access token) if permission is still granted.
 *
 * Designed to be mounted once inside the authenticated layout; safe to mount
 * multiple times (SW registration is idempotent).
 */
export const useWebPush = (
  options: UseWebPushOptions = {},
): UseWebPushResult => {
  const { enabled = true } = options;
  const support = detectSupport();
  const isSupported = support.supported;
  const supportReason = support.reason;
  const [permission, setPermission] = useState<PushPermissionStatus>(
    readCurrentPermission,
  );
  const [isSubscribed, setIsSubscribed] = useState<boolean>(false);
  const [error, setError] = useState<Error | null>(null);
  const registrationRef = useRef<ServiceWorkerRegistration | null>(null);

  useEffect(() => {
    if (!enabled || !isSupported) {
      return;
    }
    let cancelled = false;

    const init = async (): Promise<void> => {
      try {
        const credentials = readActiveCredentials();
        if (!credentials) return;

        const nav = navigator as Navigator & {
          serviceWorker: ServiceWorkerContainer;
        };
        const reg = await nav.serviceWorker.register("/sw.js", SW_SCOPE_OPTION);
        if (cancelled) return;
        const ready = (await nav.serviceWorker
          .ready) as ServiceWorkerRegistration;
        void reg;
        registrationRef.current = ready;
        const existing = await ready.pushManager.getSubscription();
        if (cancelled) return;
        setIsSubscribed(Boolean(existing));

        const isOptedOut =
          typeof localStorage !== "undefined" &&
          localStorage.getItem(PUSH_OPT_OUT_STORAGE_KEY) === "true";

        if (
          !cancelled &&
          !isOptedOut &&
          readCurrentPermission() === "granted"
        ) {
          // Re-check session right before any privileged action.
          try {
            assertSession(credentials.userId, credentials.token);
          } catch {
            return;
          }

          const saved = await getOwner();
          if (cancelled) return;

          // Stale owner from a previous user → tear down the browser-side
          // subscription and the cache so the new session starts clean.
          if (saved && saved.userId !== credentials.userId) {
            try {
              await clearLocal(ready);
              registrationRef.current = null;
              const reReg = await nav.serviceWorker.register(
                "/sw.js",
                SW_SCOPE_OPTION,
              );
              const reReady = (await nav.serviceWorker
                .ready) as ServiceWorkerRegistration;
              void reReg;
              registrationRef.current = reReady;
              setIsSubscribed(false);
              return;
            } catch {
              return;
            }
          }

          if (existing) {
            // Only fetch the current server key when the browser surfaced
            // the previous key on the existing subscription — that is the
            // only case where rotation can be detected. When applicationServerKey
            // is absent (test mocks, old browsers) we skip the rotation
            // branch entirely and re-sync the existing subscription as-is.
            const previousKey = (
              existing.options as
                | { applicationServerKey?: ArrayBuffer | Uint8Array | null }
                | undefined
            )?.applicationServerKey;
            if (previousKey !== undefined && previousKey !== null) {
              const currentKey = await notificationsApi.getVapidPublicKey();
              const newKeyBytes = base64UrlToUint8Array(currentKey);
              if (!sameVapidKey(previousKey, newKeyBytes)) {
                try {
                  await clearLocal(registrationRef.current ?? ready);
                  const reReg = await nav.serviceWorker.register(
                    "/sw.js",
                    SW_SCOPE_OPTION,
                  );
                  const reReady = (await nav.serviceWorker
                    .ready) as ServiceWorkerRegistration;
                  void reReg;
                  registrationRef.current = reReady;
                  await createPushSubscription(reReady, credentials);
                  if (!cancelled) setIsSubscribed(true);
                  return;
                } catch {
                  return;
                }
              }
            }
            await notificationsApi.subscribe(existing.toJSON());
            if (!cancelled) setIsSubscribed(true);
            return;
          }

          // Cold cache, no browser subscription: subscribe from scratch.
          try {
            await createPushSubscription(ready, credentials);
            if (!cancelled) setIsSubscribed(true);
          } catch {
            /* background sync — the user can still enable manually */
          }
        }
      } catch (caught) {
        if (!cancelled) {
          setError(pushError(caught));
        }
      }
    };

    void init();

    const nav = navigator as Navigator & {
      serviceWorker: ServiceWorkerContainer;
    };

    const handleServiceWorkerMessage = (event: MessageEvent): void => {
      const data = event.data as { type?: unknown } | null;
      if (data?.type !== PUSH_SUBSCRIPTION_CHANGE_FAILED) {
        return;
      }
      if (readCurrentPermission() !== "granted") {
        return;
      }
      const resubscribe = async (): Promise<void> => {
        const credentials = readActiveCredentials();
        if (!credentials) return;
        try {
          assertSession(credentials.userId, credentials.token);
        } catch {
          return;
        }
        try {
          const reg =
            registrationRef.current ??
            ((await nav.serviceWorker.ready) as ServiceWorkerRegistration);
          await createPushSubscription(reg, credentials);
          if (!cancelled) setIsSubscribed(true);
        } catch (caught) {
          if (!cancelled) {
            setIsSubscribed(false);
            setError(pushError(caught));
          }
        }
      };
      void resubscribe();
    };

    nav.serviceWorker.addEventListener("message", handleServiceWorkerMessage);

    return () => {
      cancelled = true;
      nav.serviceWorker.removeEventListener(
        "message",
        handleServiceWorkerMessage,
      );
    };
  }, [enabled, isSupported]);

  const ensureRegistration =
    async (): Promise<ServiceWorkerRegistration | null> => {
      if (registrationRef.current) {
        return registrationRef.current;
      }
      const nav = navigator as Navigator & {
        serviceWorker: ServiceWorkerContainer;
      };
      const reg = await nav.serviceWorker.register("/sw.js", SW_SCOPE_OPTION);
      await nav.serviceWorker.ready;
      registrationRef.current = reg;
      return reg;
    };

  const subscribe = useCallback(async (): Promise<boolean> => {
    if (!isSupported) {
      const err = makePushError(supportReason ?? "unsupported");
      setError(err);
      return false;
    }
    setError(null);
    try {
      return await withPushLock(async () => {
      const credentials = readActiveCredentials();
      if (!credentials) {
        setError(makePushError("changedSession"));
        return false;
      }
      assertSession(credentials.userId, credentials.token);

      if (typeof window === "undefined" || !window.Notification) {
        throw makePushError("unsupported");
      }
      const requested = await window.Notification.requestPermission();
      const next = requested as PushPermissionStatus;
      setPermission(next);
      if (next !== "granted") {
        setError(makePushError("denied"));
        return false;
      }
      const reg = await ensureRegistration();
      if (!reg) {
        throw makePushError("browserError");
      }
      await createPushSubscription(reg, credentials);
      if (typeof localStorage !== "undefined") {
        try {
          localStorage.removeItem(PUSH_OPT_OUT_STORAGE_KEY);
        } catch {
          /* ignore */
        }
      }
      setIsSubscribed(true);
      return true;
      });
    } catch (caught) {
      setError(pushError(caught));
      return false;
    }
  }, [isSupported, supportReason]);

  const unsubscribe = useCallback(async (): Promise<boolean> => {
    if (!isSupported) {
      return false;
    }
    setError(null);
    try {
      return await withPushLock(async () => {
      const credentials = readActiveCredentials();

      let reg = registrationRef.current;
      if (!reg) {
        const nav = navigator as Navigator & {
          serviceWorker: ServiceWorkerContainer;
        };
        const existing = await nav.serviceWorker.getRegistration();
        if (!existing) {
          return true;
        }
        reg = existing;
        registrationRef.current = existing;
      }

      const sub = await reg.pushManager.getSubscription();
      if (typeof localStorage !== "undefined") {
        try {
          localStorage.setItem(PUSH_OPT_OUT_STORAGE_KEY, "true");
        } catch {
          /* ignore */
        }
      }
      if (!sub) {
        setIsSubscribed(false);
        return true;
      }

      // If the browser-side subscription was created by a different
      // session, refuse to remove it; the next page mount will rebuild
      // ownership against the current user.
      const owner = await getOwner();
      if (owner && credentials && owner.userId !== credentials.userId) {
        return true;
      }

      await notificationsApi.unsubscribe();
      const pm = reg.pushManager as PushManager & {
        unsubscribe: () => Promise<boolean>;
      };
      await pm.unsubscribe();
      await clearOwner();
      setIsSubscribed(false);
      return true;
      });
    } catch (caught) {
      setError(pushError(caught));
      return false;
    }
  }, [isSupported]);

  return {
    isSupported,
    permission,
    isSubscribed,
    subscribe,
    unsubscribe,
    error,
  };
};
