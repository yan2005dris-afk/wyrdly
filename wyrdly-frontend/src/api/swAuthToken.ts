/**
 * IndexedDB-backed JWT store used by the Service Worker to authenticate
 * its own outbound fetches. The page writes the token here whenever the
 * auth state changes (login, refresh, register, logout); the SW reads it
 * from the same store when it needs to call the notifications endpoint
 * after a push event lands.
 *
 * Why IndexedDB and not localStorage or cookies: the SW context does not
 * share a `document` with the page, so `localStorage` is unreachable
 * from it, and HttpOnly cookies are not an option because the backend
 * authenticates with a JWT in the Authorization header. IndexedDB is
 * the only synchronous-storage area that both contexts can read and
 * write from the same origin.
 *
 * Security note: any script on the origin can read this value, so a
 * successful XSS would expose the JWT. The standard mitigations apply
 * (strict CSP, short-lived tokens, refresh rotation).
 */
const AUTH_DB_NAME = "wyrdly-auth";
const AUTH_STORE_NAME = "auth";
const TOKEN_KEY = "jwt";

const isIndexedDbAvailable = (): boolean =>
  typeof globalThis !== "undefined" &&
  typeof globalThis.indexedDB !== "undefined";

/**
 * Opens (and migrates) the auth database. Always resolves with the IDBDatabase
 * or null if IndexedDB is unavailable; never throws, because failure here
 * must not break login/logout flows.
 */
const openAuthDb = (): Promise<IDBDatabase | null> =>
  new Promise((resolve) => {
    if (!isIndexedDbAvailable()) return resolve(null);
    let req: IDBOpenDBRequest;
    try {
      req = globalThis.indexedDB.open(AUTH_DB_NAME, 1);
    } catch {
      return resolve(null);
    }
    req.onupgradeneeded = () => {
      try {
        const db = req.result;
        if (!db.objectStoreNames.contains(AUTH_STORE_NAME)) {
          db.createObjectStore(AUTH_STORE_NAME);
        }
      } catch {
        /* migration failure is non-fatal; subsequent reads will retry */
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => resolve(null);
    req.onblocked = () => resolve(null);
  });

/**
 * Writes the JWT to IndexedDB, or removes it when called with null. Resolves
 * silently on any failure so the caller's auth state machine is not blocked
 * by storage errors.
 */
export const persistAuthToken = async (
  token: string | null,
): Promise<void> => {
  if (!isIndexedDbAvailable()) return;
  const db = await openAuthDb();
  if (!db) return;
  await new Promise<void>((resolve) => {
    try {
      const tx = db.transaction(AUTH_STORE_NAME, "readwrite");
      const store = tx.objectStore(AUTH_STORE_NAME);
      if (token) store.put(token, TOKEN_KEY);
      else store.delete(TOKEN_KEY);
      tx.oncomplete = () => resolve();
      tx.onerror = () => resolve();
      tx.onabort = () => resolve();
    } catch {
      resolve();
    }
  });
  try {
    db.close();
  } catch {
    /* ignore */
  }
};

/**
 * Reads the JWT from IndexedDB, or null if no token is stored or the API
 * is unavailable. Mirrored verbatim by the Service Worker so both contexts
 * agree on the storage contract.
 */
export const getAuthToken = async (): Promise<string | null> => {
  if (!isIndexedDbAvailable()) return null;
  const db = await openAuthDb();
  if (!db) return null;
  const result = await new Promise<string | null>((resolve) => {
    try {
      const tx = db.transaction(AUTH_STORE_NAME, "readonly");
      const req = tx.objectStore(AUTH_STORE_NAME).get(TOKEN_KEY);
      req.onsuccess = () =>
        resolve(typeof req.result === "string" ? req.result : null);
      req.onerror = () => resolve(null);
    } catch {
      resolve(null);
    }
  });
  try {
    db.close();
  } catch {
    /* ignore */
  }
  return result;
};

/** Convenience alias for logout flows. */
export const clearAuthToken = async (): Promise<void> => {
  await persistAuthToken(null);
};
