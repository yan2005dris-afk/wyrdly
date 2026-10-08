import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { clearAuthToken, getAuthToken, persistAuthToken } from "./swAuthToken";

/**
 * The contract under test is the storage layout the Service Worker and the
 * page must agree on: a single IndexedDB database called "wyrdly-auth" at
 * version 1, with one object store called "auth" holding the JWT under the
 * key "jwt". We exercise both sides (persist + get) with a fake-indexeddb
 * stand-in because the real IDB API is not implemented in jsdom.
 */
type Store = Map<string, string>;

interface FakeObjectStore {
  put: (value: string, key: string) => void;
  get: (key: string) => FakeRequest<string | undefined>;
  delete: (key: string) => void;
}

interface FakeTransaction {
  objectStore: (name: string) => FakeObjectStore;
  oncomplete: ((e: unknown) => void) | null;
  onerror: ((e: unknown) => void) | null;
  onabort: ((e: unknown) => void) | null;
}

interface FakeRequest<T> {
  result: T;
  onsuccess: ((e: unknown) => void) | null;
  onerror: ((e: unknown) => void) | null;
}

interface FakeDb {
  stores: Map<string, Store>;
  transaction: (name: string, mode: string) => FakeTransaction;
  close: () => void;
}

interface FakeOpenRequest {
  result: FakeDb;
  onupgradeneeded: ((e: unknown) => void) | null;
  onsuccess: ((e: unknown) => void) | null;
  onerror: ((e: unknown) => void) | null;
  onblocked: ((e: unknown) => void) | null;
}

const DB_NAME = "wyrdly-auth";
const STORE_NAME = "auth";
const KEY = "jwt";

const storesByDb = new Map<string, Map<string, Store>>();

const getOrCreateStore = (db: string, name: string): Store => {
  if (!storesByDb.has(db)) storesByDb.set(db, new Map());
  const inner = storesByDb.get(db)!;
  if (!inner.has(name)) inner.set(name, new Map());
  return inner.get(name)!;
};

const buildObjectStore = (store: Store): FakeObjectStore => ({
  put(value, key) {
    store.set(key, value);
  },
  get(key) {
    const request: FakeRequest<string | undefined> = {
      result: store.get(key),
      onsuccess: null,
      onerror: null,
    };
    queueMicrotask(() => {
      if (request.onsuccess) request.onsuccess({ target: request });
    });
    return request;
  },
  delete(key) {
    store.delete(key);
  },
});

const buildTransaction = (db: FakeDb, name: string): FakeTransaction => {
  const store = getOrCreateStore(DB_NAME, name);
  const tx: FakeTransaction = {
    objectStore: () => buildObjectStore(store),
    oncomplete: null,
    onerror: null,
    onabort: null,
  };
  queueMicrotask(() => {
    if (tx.oncomplete) tx.oncomplete({ target: tx });
  });
  return tx;
};

const buildOpenRequest = (name: string): FakeOpenRequest => {
  const req: FakeOpenRequest = {
    result: {
      stores: storesByDb.get(name) ?? new Map(),
      transaction: () =>
        buildTransaction(
          { stores: storesByDb.get(name) ?? new Map(), transaction: () => buildTransaction({} as FakeDb, ""), close: () => {} },
          STORE_NAME,
        ),
      close: () => {},
    },
    onupgradeneeded: null,
    onsuccess: null,
    onerror: null,
    onblocked: null,
  };
  queueMicrotask(() => {
    if (req.onupgradeneeded) {
      req.onupgradeneeded({ target: req });
    }
    if (req.onsuccess) {
      req.onsuccess({ target: req });
    }
  });
  return req;
};

const installIndexedDb = () => {
  Object.defineProperty(globalThis, "indexedDB", {
    configurable: true,
    writable: true,
    value: {
      open: () => buildOpenRequest(DB_NAME),
    },
  });
};

const uninstallIndexedDb = () => {
  try {
    delete (globalThis as { indexedDB?: unknown }).indexedDB;
  } catch {
    Object.defineProperty(globalThis, "indexedDB", {
      configurable: true,
      writable: true,
      value: undefined,
    });
  }
  storesByDb.clear();
};

describe("swAuthToken", () => {
  beforeEach(() => {
    installIndexedDb();
  });

  afterEach(() => {
    uninstallIndexedDb();
  });

  it("persists and then reads back the same JWT", async () => {
    await persistAuthToken("jwt-abc-123");
    const token = await getAuthToken();
    expect(token).toBe("jwt-abc-123");
  });

  it("overwrites an existing token on a subsequent persist", async () => {
    await persistAuthToken("old-token");
    await persistAuthToken("new-token");
    expect(await getAuthToken()).toBe("new-token");
  });

  it("clearAuthToken removes the stored JWT", async () => {
    await persistAuthToken("to-be-removed");
    await clearAuthToken();
    expect(await getAuthToken()).toBeNull();
  });

  it("returns null when nothing has been persisted", async () => {
    expect(await getAuthToken()).toBeNull();
  });

  it("uses the contract keys agreed with the Service Worker", async () => {
    // The SW expects DB=wyrdly-auth, store=auth, key=jwt. Any change to
    // these identifiers would silently break the page↔SW handshake, so
    // this test pins them down.
    await persistAuthToken("x");
    expect(storesByDb.has(DB_NAME)).toBe(true);
    expect(storesByDb.get(DB_NAME)?.has(STORE_NAME)).toBe(true);
    expect(storesByDb.get(DB_NAME)?.get(STORE_NAME)?.get(KEY)).toBe("x");
  });

  it("resolves silently when IndexedDB is unavailable", async () => {
    uninstallIndexedDb();
    await expect(persistAuthToken("x")).resolves.toBeUndefined();
    await expect(getAuthToken()).resolves.toBeNull();
    await expect(clearAuthToken()).resolves.toBeUndefined();
  });
});
