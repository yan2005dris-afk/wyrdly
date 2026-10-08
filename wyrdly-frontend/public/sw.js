/**
 * Wyrdly Service Worker — Web Push delivery.
 *
 * Responsibilities:
 *  - Lifecycle (install / activate) with skipWaiting + clients.claim so updates
 *    take over fast.
 *  - `push` — decode JSON payload, render `showNotification` with title/body/icon/data,
 *    sync OS-level app badge, and broadcast event to open windows.
 *  - `notificationclick` — focus existing window on same origin or open target URL.
 *  - `pushsubscriptionchange` — re-subscribe silently and POST to backend.
 */
const VAPID_PUBLIC_KEY_URL = "/api/notifications/vapid-public-key";
const SUBSCRIBE_URL = "/api/notifications/subscribe";
const PUSH_CHANGE_FAILED = "push-subscription-change-failed";

const isServiceWorkerContext = () =>
  typeof ServiceWorkerGlobalScope !== "undefined" &&
  typeof self !== "undefined" &&
  self instanceof ServiceWorkerGlobalScope;

function urlBase64ToUint8Array(base64String) {
  const trimmed = String(base64String).trim();
  const padding = "=".repeat((4 - (trimmed.length % 4)) % 4);
  const base64 = (trimmed + padding).replace(/-/g, "+").replace(/_/g, "/");
  const raw = atob(base64);
  const out = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i += 1) {
    out[i] = raw.charCodeAt(i);
  }
  return out;
}

// Reuse a single BroadcastChannel for the lifetime of the SW.
let pushChannel = null;
function getPushChannel() {
  if (pushChannel === null && typeof BroadcastChannel !== "undefined") {
    try {
      pushChannel = new BroadcastChannel("wyrdly-notifications");
    } catch (_err) {
      pushChannel = null;
    }
  }
  return pushChannel;
}

async function broadcastPushReceived(payload) {
  const message = {
    type: "wyrdly:push-received",
    payload,
  };

  // 1. BroadcastChannel: persistent channel instance
  const channel = getPushChannel();
  if (channel !== null) {
    try {
      channel.postMessage(message);
    } catch (_err) {
      /* BroadcastChannel fallback */
    }
  }

  // 2. Window clients fallback (for clients not yet connected to BroadcastChannel)
  if (self.clients && typeof self.clients.matchAll === "function") {
    try {
      const windowClients = await self.clients.matchAll({
        type: "window",
        includeUncontrolled: true,
      });
      for (const client of windowClients) {
        if ("postMessage" in client) {
          client.postMessage(message);
        }
      }
    } catch (_err) {
      /* clients.matchAll fallback */
    }
  }
}

async function handlePush(event) {
  if (!event || !event.data) {
    return;
  }
  let payload;
  try {
    payload = event.data.json();
  } catch (_err) {
    payload = { title: "Wyrdly", body: event.data.text() };
  }
  const title = payload.title || "Wyrdly";
  const tag = payload.data?.postId
    ? `post-${payload.data.postId}`
    : payload.data?.type
      ? `type-${payload.data.type}`
      : undefined;

  const options = {
    body: payload.body || "",
    icon: payload.icon || "/icons/wyrdly-icon-192.png",
    badge: payload.badge || "/icons/wyrdly-badge-72.png",
    tag,
    data: payload.data || {},
  };
  await self.registration.showNotification(title, options);

  // Update OS-level app badge if supported
  if (
    self.registration &&
    typeof self.registration.getNotifications === "function" &&
    self.navigator &&
    typeof self.navigator.setAppBadge === "function"
  ) {
    try {
      const visible = await self.registration.getNotifications();
      const unread = visible.length;
      if (unread > 0) {
        await self.navigator.setAppBadge(unread);
      } else if (typeof self.navigator.clearAppBadge === "function") {
        await self.navigator.clearAppBadge();
      }
    } catch (_err) {
      /* setAppBadge unsupported on this UA */
    }
  }

  await broadcastPushReceived(payload);
}

async function handleNotificationClick(event) {
  if (!event || !event.notification) {
    return;
  }
  event.notification.close();
  const targetUrl =
    (event.notification.data && event.notification.data.url) || "/";
  const allClients = await self.clients.matchAll({
    type: "window",
    includeUncontrolled: true,
  });
  for (const client of allClients) {
    if (
      "focus" in client &&
      typeof client.url === "string" &&
      new URL(client.url).origin === self.location.origin
    ) {
      await client.focus();
      const expectedHref = new URL(targetUrl, self.location.origin).href;
      if (client.url !== expectedHref && "navigate" in client) {
        try {
          await client.navigate(targetUrl);
        } catch (_err) {
          /* navigation may be blocked; fall through to openWindow */
        }
      }
      return;
    }
  }
  await self.clients.openWindow(targetUrl);
}

async function handleSubscriptionChange(event) {
  if (!self.registration || !self.registration.pushManager) {
    return;
  }
  try {
    const response = await fetch(VAPID_PUBLIC_KEY_URL);
    if (!response.ok) {
      throw new Error(
        `VAPID public key fetch failed: ${response.status} ${response.statusText}`,
      );
    }
    const vapidKey = (await response.text()).trim();
    const newSub = await self.registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(vapidKey),
    });
    await fetch(SUBSCRIBE_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(newSub.toJSON ? newSub.toJSON() : newSub),
      credentials: "include",
    });
  } catch (err) {
    const clients = await self.clients.matchAll({
      type: "window",
      includeUncontrolled: true,
    });
    for (const client of clients) {
      client.postMessage({ type: PUSH_CHANGE_FAILED, error: String(err) });
    }
  }
}

function handleInstall(_event) {
  if (self.skipWaiting) {
    self.skipWaiting();
  }
}

function handleActivate(event) {
  if (event && typeof event.waitUntil === "function") {
    event.waitUntil(
      (async () => {
        if (self.clients && typeof self.clients.claim === "function") {
          await self.clients.claim();
        }
      })(),
    );
  } else if (self.clients && typeof self.clients.claim === "function") {
    self.clients.claim();
  }
}

if (typeof globalThis !== "undefined") {
  globalThis.__wyrdlySW = {
    handlePush,
    broadcastPushReceived,
    handleNotificationClick,
    handleSubscriptionChange,
    handleInstall,
    handleActivate,
    urlBase64ToUint8Array,
  };
}

if (isServiceWorkerContext()) {
  self.addEventListener("install", handleInstall);
  self.addEventListener("activate", handleActivate);
  self.addEventListener("push", (event) => {
    if (event && typeof event.waitUntil === "function") {
      event.waitUntil(handlePush(event));
    } else {
      handlePush(event);
    }
  });
  self.addEventListener("notificationclick", (event) => {
    if (event && typeof event.waitUntil === "function") {
      event.waitUntil(handleNotificationClick(event));
    } else {
      handleNotificationClick(event);
    }
  });
  self.addEventListener("pushsubscriptionchange", (event) => {
    if (event && typeof event.waitUntil === "function") {
      event.waitUntil(handleSubscriptionChange(event));
    } else {
      handleSubscriptionChange(event);
    }
  });
}
