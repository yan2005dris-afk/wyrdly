/**
 * Cache-API backed storage for the Web Push owner (the userId/endpoint pair
 * that the current browser subscription belongs to).
 *
 * The Cache API is preferred over localStorage because:
 *  - It survives a service-worker update or context loss better than DOM
 *    storage in some browsers.
 *  - It is scope-respecting: a `/sw.js` change that would clear SW caches
 *    also clears the cached state, forcing the page to re-establish
 *    ownership on the next mount.
 *
 * The cache name and state URL are exported as constants so tests can
 * target them directly. Bumping the cache version is the migration
 * mechanism when the on-disk shape changes.
 */
export const PUSH_CACHE_NAME = "wyrdly-push-v1";
export const PUSH_STATE_URL = "/__wyrdly_push_state__";

export interface PushOwner {
  readonly userId: string;
  readonly endpoint: string;
}

interface CacheLike {
  match(request: Request | string): Promise<Response | undefined>;
  put(request: Request | string, response: Response): Promise<void>;
  delete(request: Request | string): Promise<boolean>;
}

interface CacheStorageLike {
  open(name: string): Promise<CacheLike>;
}

const cacheStorage = (): CacheStorageLike | null => {
  if (typeof caches === "undefined") return null;
  return caches as unknown as CacheStorageLike;
};

export async function getOwner(): Promise<PushOwner | null> {
  const storage = cacheStorage();
  if (!storage) return null;
  try {
    const cache = await storage.open(PUSH_CACHE_NAME);
    const response = await cache.match(PUSH_STATE_URL);
    if (!response) return null;
    const body = (await response.json()) as Partial<PushOwner>;
    if (typeof body.userId !== "string" || typeof body.endpoint !== "string") {
      return null;
    }
    return { userId: body.userId, endpoint: body.endpoint };
  } catch {
    return null;
  }
}

export async function setOwner(owner: PushOwner): Promise<void> {
  const storage = cacheStorage();
  if (!storage) {
    throw new Error("Cache API unavailable");
  }
  const cache = await storage.open(PUSH_CACHE_NAME);
  await cache.put(
    PUSH_STATE_URL,
    new Response(JSON.stringify(owner), {
      headers: { "Content-Type": "application/json" },
    }),
  );
}

export async function clearOwner(): Promise<void> {
  const storage = cacheStorage();
  if (!storage) return;
  try {
    const cache = await storage.open(PUSH_CACHE_NAME);
    await cache.delete(PUSH_STATE_URL);
  } catch {
    /* best effort */
  }
}

/**
 * Best-effort browser-side cleanup after a session change or a failed
 * subscription attempt. Closes any visible notifications, removes the
 * cached owner, and asks the browser to release its subscription.
 *
 * Returns true when no subscription remains afterwards (the caller's
 * "clean slate" invariant). Returns false when the browser refused to
 * drop the subscription, in which case the next subscribe() will throw
 * and surface `browserError` to the user.
 */
export async function clearLocal(
  registration: ServiceWorkerRegistration,
): Promise<boolean> {
  await clearOwner();
  if (typeof registration.getNotifications === "function") {
    const visible = await registration.getNotifications();
    for (const notification of visible) {
      notification.close();
    }
  }
  const subscription = await registration.pushManager.getSubscription();
  if (!subscription) return true;
  const released = await subscription.unsubscribe();
  if (!released) {
    const stillThere = await registration.pushManager.getSubscription();
    return stillThere === null;
  }
  return true;
}
