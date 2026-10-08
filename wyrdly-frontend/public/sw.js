/* eslint-disable no-restricted-globals */
/**
 * Wyrdly Service Worker — Web Push delivery.
 *
 * Responsibilities:
 *  - Lifecycle (install / activate) with skipWaiting + clients.claim so updates
 *    take over fast.
 *  - `push` — decode the JSON payload sent by the backend's
 *    `PushDispatcherImpl`, render a `showNotification` with the title/body/icon
 *    the payload carries, and preserve `data` for the click handler. After
 *    showing the OS notification, the SW fetches the authoritative
 *    notifications feed (authenticated via the JWT stored in IndexedDB by
 *    the page) and broadcasts the real data back to every client so the
 *    React UI can replace the optimistic update with ground truth.
 *  - `notificationclick` — focus an existing tab on the same origin if any,
 *    otherwise open a new window on `data.url`. Falls back to `/` if the
 *    payload had no URL.
 *  - `pushsubscriptionchange` — re-subscribe silently using the current VAPID
 *    key, then POST the new subscription to the backend. If re-subscription
 *    fails, postMessage every open client so the React app can retry when the
 *    network is back.
 *  - `message` (type=refresh-now) — page-triggered refresh: the page asks the
 *    SW to fetch the latest notifications and post them back. This lets the
 *    page reconcile after returning from a backgrounded tab without paying
 *    the cost of an XHR from inside a throttled event loop.
 *
 * The handler bodies are exposed as pure functions on `globalThis.__wyrdlySW`
 * so the Vitest suite can exercise them against a mocked `self` without
 * spinning up a real ServiceWorkerGlobalScope.
 */
const VAPID_PUBLIC_KEY_URL = "/api/notifications/vapid-public-key";
const SUBSCRIBE_URL = "/api/notifications/subscribe";
const PUSH_CHANGE_FAILED = "push-subscription-change-failed";
const NOTIFICATIONS_URL = "/api/notifications?page=0&pageSize=20";

// IndexedDB-backed JWT store. Mirrored byte-for-byte in
// `src/api/swAuthToken.ts` (the page side) so both contexts agree on the
// same key, store name, and DB version.
const AUTH_DB_NAME = "wyrdly-auth";
const AUTH_STORE_NAME = "auth";
const AUTH_TOKEN_KEY = "jwt";

const isServiceWorkerContext = () =>
  typeof ServiceWorkerGlobalScope !== "undefined" &&
  typeof self !== "undefined" &&
  self instanceof ServiceWorkerGlobalScope;

function getAuthToken() {
  return new Promise((resolve) => {
    if (
      typeof globalThis === "undefined" ||
      typeof globalThis.indexedDB === "undefined"
    ) {
      return resolve(null);
    }
    let req;
    try {
      req = globalThis.indexedDB.open(AUTH_DB_NAME, 1);
    } catch (_err) {
      return resolve(null);
    }
    req.onupgradeneeded = () => {
      try {
        const db = req.result;
        if (!db.objectStoreNames.contains(AUTH_STORE_NAME)) {
          db.createObjectStore(AUTH_STORE_NAME);
        }
      } catch (_err) {
        /* ignore */
      }
    };
    req.onsuccess = () => {
      try {
        const db = req.result;
        const tx = db.transaction(AUTH_STORE_NAME, "readonly");
        const getReq = tx.objectStore(AUTH_STORE_NAME).get(AUTH_TOKEN_KEY);
        getReq.onsuccess = () =>
          resolve(typeof getReq.result === "string" ? getReq.result : null);
        getReq.onerror = () => resolve(null);
      } catch (_err) {
        resolve(null);
      }
    };
    req.onerror = () => resolve(null);
    req.onblocked = () => resolve(null);
  });
}

async function refreshNotifications() {
  const token = await getAuthToken();
  if (!token) {
    return null;
  }
  try {
    const response = await fetch(NOTIFICATIONS_URL, {
      headers: { Authorization: `Bearer ${token}` },
      credentials: "include",
    });
    if (!response.ok) {
      return null;
    }
    return await response.json();
  } catch (_err) {
    return null;
  }
}

async function broadcastNotificationsRefreshed(feed, targetClient) {
  const message = {
    type: "wyrdly:notifications-refreshed",
    payload: feed,
  };
  // 1. Direct reply to the page that asked (when this is a refresh-now).
  if (targetClient && typeof targetClient.postMessage === "function") {
    try {
      targetClient.postMessage(message);
    } catch (_err) {
      /* ignore */
    }
    return;
  }
  // 2. Fan-out: every connected window client. This is the path the push
  // handler takes; if a tab is backgrounded its message is throttled but
  // the controller-driven `refresh-now` listener covers that case when
  // the tab eventually returns to the foreground.
  const channel = getPushChannel();
  if (channel !== null) {
    try {
      channel.postMessage(message);
    } catch (_err) {
      /* ignore */
    }
  }
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
      /* ignore */
    }
  }
}

async function handleRefreshRequest(event) {
  const source = event && event.source;
  const feed = await refreshNotifications();
  if (feed) {
    await broadcastNotificationsRefreshed(feed, source);
    if (
      self.navigator &&
      typeof self.navigator.setAppBadge === "function" &&
      typeof feed.unreadCount === "number"
    ) {
      try {
        if (feed.unreadCount > 0) {
          await self.navigator.setAppBadge(feed.unreadCount);
        } else if (typeof self.navigator.clearAppBadge === "function") {
          await self.navigator.clearAppBadge();
        }
      } catch (_err) {
        /* ignore */
      }
    }
  }
}

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

// Reuse a single BroadcastChannel for the lifetime of the SW. Creating a
// fresh channel per push and closing it 1s later was both wasteful and
// racy: any push that arrived while the React side was re-mounting its
// listener (due to the unstable queryKey) would land on a closing channel
// and be dropped. A persistent module-level channel avoids that entirely.
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

  // 1. BroadcastChannel: same channel instance for the SW's lifetime.
  const channel = getPushChannel();
  if (channel !== null) {
    try {
      channel.postMessage(message);
    } catch (_err) {
      /* BroadcastChannel error fallback */
    }
  }

  // 2. Also postMessage to matched window clients (covers tabs that the
  // BroadcastChannel may not have reached — e.g. controlled clients with
  // a fresh controller before the channel is open).
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

  // Update the OS-level app badge. This path does NOT depend on the tab
  // being controlled, focused, or even alive: setAppBadge is a UA-level
  // surface, so it works even when clients.matchAll() returns [] because
  // the SW was just woken up from a stopped state and the controller
  // relationship is being re-established. The page-side hook will
  // reconcile the badge with the authoritative unreadCount once the tab
  // becomes active again.
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

  // Fetch the authoritative notifications feed and broadcast it to every
  // client so the React UI can replace the optimistic update with the
  // real data. Runs after the broadcast because the optimistic payload
  // is already in flight; if the fetch fails the page can still fall back
  // to the 600ms-delayed refetch in useNotifications.
  const feed = await refreshNotifications();
  if (feed) {
    await broadcastNotificationsRefreshed(feed);
    if (
      self.navigator &&
      typeof self.navigator.setAppBadge === "function" &&
      typeof feed.unreadCount === "number"
    ) {
      try {
        if (feed.unreadCount > 0) {
          await self.navigator.setAppBadge(feed.unreadCount);
        } else if (typeof self.navigator.clearAppBadge === "function") {
          await self.navigator.clearAppBadge();
        }
      } catch (_err) {
        /* setAppBadge failure is cosmetic */
      }
    }
  }
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
    broadcastNotificationsRefreshed,
    handleNotificationClick,
    handleSubscriptionChange,
    handleRefreshRequest,
    handleInstall,
    handleActivate,
    refreshNotifications,
    getAuthToken,
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
  self.addEventListener("message", (event) => {
    if (
      event &&
      event.data &&
      typeof event.data === "object" &&
      event.data.type === "refresh-now"
    ) {
      if (event && typeof event.waitUntil === "function") {
        event.waitUntil(handleRefreshRequest(event));
      } else {
        handleRefreshRequest(event);
      }
    }
  });
}
