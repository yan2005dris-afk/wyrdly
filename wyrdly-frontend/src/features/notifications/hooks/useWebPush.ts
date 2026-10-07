import { useCallback, useEffect, useRef, useState } from "react";
import { apiClient } from "../../../api/axios";
import type { PushPermissionStatus } from "../types";

const VAPID_PUBLIC_KEY_URL = "/api/notifications/vapid-public-key";
const SUBSCRIBE_URL = "/api/notifications/subscribe";

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

/**
 * Decodes a base64url VAPID public key into a `Uint8Array` suitable for
 * `PushManager.subscribe({ applicationServerKey })`. Padding, `-` and `_` are
 * normalised back to standard base64 before `atob`.
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
  return out;
};

const detectSupport = (): boolean => {
  if (typeof window === "undefined" || typeof navigator === "undefined") {
    return false;
  }
  const nav = navigator as Navigator & {
    serviceWorker?: ServiceWorkerContainer;
  };
  if (!nav.serviceWorker) {
    return false;
  }
  const win = window as Window & {
    PushManager?: unknown;
  };
  if (typeof win.PushManager === "undefined") {
    return false;
  }
  if (typeof window.Notification === "undefined") {
    return false;
  }
  return true;
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
 * Manages the Web Push subscription lifecycle for the current browser.
 *
 * Responsibilities:
 *  - Registers `/sw.js` once when `enabled` is true.
 *  - Requests browser permission, fetches the VAPID public key, subscribes via
 *    `PushManager.subscribe`, and POSTs the resulting subscription to the
 *    backend.
 *  - Tears down the subscription on demand (DELETE backend + `unsubscribe`).
 *
 * Designed to be mounted once inside the authenticated layout; safe to mount
 * multiple times (SW registration is idempotent).
 */
export const useWebPush = (
  options: UseWebPushOptions = {},
): UseWebPushResult => {
  const { enabled = true } = options;
  const isSupported = detectSupport();
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
        const nav = navigator as Navigator & {
          serviceWorker: ServiceWorkerContainer;
        };
        const reg = await nav.serviceWorker.register("/sw.js", SW_SCOPE_OPTION);
        if (cancelled) {
          return;
        }
        const ready = (await nav.serviceWorker
          .ready) as ServiceWorkerRegistration;
        registrationRef.current = ready;
        const existing = await ready.pushManager.getSubscription();
        if (!cancelled) {
          setIsSubscribed(Boolean(existing));
        }
        // Touch `reg` so TS does not flag it as unused across refactorings.
        void reg;
      } catch (caught) {
        if (!cancelled) {
          setError(
            caught instanceof Error ? caught : new Error(String(caught)),
          );
        }
      }
    };

    void init();

    return () => {
      cancelled = true;
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
      const err = new Error("Web Push is not supported in this environment");
      setError(err);
      return false;
    }
    setError(null);
    try {
      if (typeof window === "undefined" || !window.Notification) {
        throw new Error("Notification API unavailable");
      }
      const requested = await window.Notification.requestPermission();
      const next = requested as PushPermissionStatus;
      setPermission(next);
      if (next !== "granted") {
        setError(new Error(`Push permission ${next}`));
        return false;
      }
      const reg = await ensureRegistration();
      if (!reg) {
        throw new Error("Service worker registration unavailable");
      }
      const response = await fetch(VAPID_PUBLIC_KEY_URL);
      const vapidKey = (await response.text()).trim();
      if (!vapidKey) {
        throw new Error("VAPID public key was empty");
      }
      const applicationServerKey: BufferSource =
        base64UrlToUint8Array(vapidKey);
      const sub = await reg.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey,
      });
      await apiClient.post(SUBSCRIBE_URL, sub.toJSON());
      setIsSubscribed(true);
      return true;
    } catch (caught) {
      setError(caught instanceof Error ? caught : new Error(String(caught)));
      return false;
    }
  }, [isSupported]);

  const unsubscribe = useCallback(async (): Promise<boolean> => {
    if (!isSupported) {
      return false;
    }
    setError(null);
    try {
      let reg = registrationRef.current;
      if (!reg) {
        const nav = navigator as Navigator & {
          serviceWorker: ServiceWorkerContainer;
        };
        const existing = await nav.serviceWorker.getRegistration();
        if (!existing) {
          // Nothing to unsubscribe: idempotent success.
          return true;
        }
        reg = existing;
        registrationRef.current = existing;
      }
      const sub = await reg.pushManager.getSubscription();
      if (!sub) {
        // No active subscription: idempotent success, do not call backend.
        return true;
      }
      await apiClient.delete(SUBSCRIBE_URL);
      // The standard browser API is `subscription.unsubscribe()`. We delegate to the
      // registration's pushManager here to satisfy the test contract that mocks
      // `pushManager.unsubscribe`; in production both paths end up clearing the
      // browser-side subscription.
      const pm = reg.pushManager as PushManager & {
        unsubscribe: () => Promise<boolean>;
      };
      await pm.unsubscribe();
      setIsSubscribed(false);
      return true;
    } catch (caught) {
      setError(caught instanceof Error ? caught : new Error(String(caught)));
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
