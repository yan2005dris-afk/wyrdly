import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  PUSH_CACHE_NAME,
  PUSH_STATE_URL,
  clearLocal,
  clearOwner,
  getOwner,
  setOwner,
} from "./pushCache";

interface FakeCache {
  store: Map<string, Response>;
  match: ReturnType<typeof vi.fn>;
  put: ReturnType<typeof vi.fn>;
  delete: ReturnType<typeof vi.fn>;
}

const makeCache = (): FakeCache => {
  const store = new Map<string, Response>();
  const match = vi.fn(async (req: Request | string) => {
    const key = typeof req === "string" ? req : req.url;
    return store.get(key);
  });
  const put = vi.fn(async (req: Request | string, response: Response) => {
    const key = typeof req === "string" ? req : req.url;
    store.set(key, response);
  });
  const del = vi.fn(async (req: Request | string) => {
    const key = typeof req === "string" ? req : req.url;
    return store.delete(key);
  });
  return { store, match, put, delete: del };
};

const installCachesMock = () => {
  const fakeCache = makeCache();
  const open = vi.fn(async () => fakeCache);
  const original = (globalThis as { caches?: unknown }).caches;
  Object.defineProperty(globalThis, "caches", {
    value: { open },
    configurable: true,
    writable: true,
  });
  return { fakeCache, open, restore: () => {
    if (original === undefined) {
      delete (globalThis as { caches?: unknown }).caches;
    } else {
      Object.defineProperty(globalThis, "caches", {
        value: original,
        configurable: true,
        writable: true,
      });
    }
  } };
};

describe("pushCache", () => {
  let shim: ReturnType<typeof installCachesMock>;

  beforeEach(() => {
    shim = installCachesMock();
  });

  afterEach(() => {
    shim.restore();
  });

  it("round-trips an owner through the cache", async () => {
    expect(await getOwner()).toBeNull();

    await setOwner({ userId: "usr_a", endpoint: "https://push.example.com/x" });

    const owner = await getOwner();
    expect(owner).toEqual({
      userId: "usr_a",
      endpoint: "https://push.example.com/x",
    });
    expect(shim.fakeCache.put).toHaveBeenCalledWith(
      PUSH_STATE_URL,
      expect.any(Response),
    );
    expect(shim.open).toHaveBeenCalledWith(PUSH_CACHE_NAME);
  });

  it("returns null when the cached payload has the wrong shape", async () => {
    const fakeCache = shim.fakeCache;
    fakeCache.store.set(
      PUSH_STATE_URL,
      new Response(JSON.stringify({ wrong: "shape" }), {
        headers: { "Content-Type": "application/json" },
      }),
    );

    expect(await getOwner()).toBeNull();
  });

  it("ignores cache errors during read", async () => {
    shim.open.mockRejectedValueOnce(new Error("quota exceeded"));
    expect(await getOwner()).toBeNull();
  });

  it("clearOwner removes the cached entry", async () => {
    await setOwner({ userId: "usr_a", endpoint: "https://push.example.com/x" });
    await clearOwner();
    expect(await getOwner()).toBeNull();
    expect(shim.fakeCache.delete).toHaveBeenCalledWith(PUSH_STATE_URL);
  });

  it("clearOwner swallows cache errors", async () => {
    shim.fakeCache.delete.mockRejectedValueOnce(new Error("oh no"));
    await expect(clearOwner()).resolves.toBeUndefined();
  });

  it("setOwner throws a useful error when caches is unavailable", async () => {
    shim.restore();
    delete (globalThis as { caches?: unknown }).caches;
    await expect(
      setOwner({ userId: "u", endpoint: "https://x/y" }),
    ).rejects.toThrow(/Cache API unavailable/);
  });
});

describe("clearLocal", () => {
  let shim: ReturnType<typeof installCachesMock>;

  beforeEach(() => {
    shim = installCachesMock();
  });

  afterEach(() => {
    shim.restore();
  });

  const makeRegistration = (
    subscription: { unsubscribe: ReturnType<typeof vi.fn> } | null,
    getSubscription: ReturnType<typeof vi.fn>,
    notifications: Array<{ close: ReturnType<typeof vi.fn> }> = [],
  ) => {
    const unsubscribe = subscription?.unsubscribe ?? vi.fn();
    if (subscription) subscription.unsubscribe = unsubscribe;
    return {
      pushManager: {
        getSubscription: getSubscription ?? vi.fn(async () => subscription),
      },
      getNotifications: vi.fn(async () => notifications),
    } as unknown as ServiceWorkerRegistration;
  };

  it("closes notifications, drops cache, unsubscribes and reports clean slate", async () => {
    const unsubscribe = vi.fn(async () => true);
    const sub = { unsubscribe };
    const closeA = vi.fn();
    const closeB = vi.fn();
    const reg = makeRegistration(sub, vi.fn(async () => sub), [
      { close: closeA },
      { close: closeB },
    ]);
    await setOwner({ userId: "u", endpoint: "https://x/y" });

    const ok = await clearLocal(reg);

    expect(closeA).toHaveBeenCalledTimes(1);
    expect(closeB).toHaveBeenCalledTimes(1);
    expect(unsubscribe).toHaveBeenCalledTimes(1);
    expect(await getOwner()).toBeNull();
    expect(ok).toBe(true);
  });

  it("returns true when no subscription exists (idempotent cleanup)", async () => {
    const reg = makeRegistration(null, vi.fn(async () => null), []);
    const ok = await clearLocal(reg);
    expect(ok).toBe(true);
  });

  it("returns false when the browser refuses to release the subscription", async () => {
    const unsubscribe = vi.fn(async () => false);
    const sub = { unsubscribe };
    const reg = makeRegistration(sub, vi.fn(async () => sub), []);
    const ok = await clearLocal(reg);
    expect(ok).toBe(false);
    expect(unsubscribe).toHaveBeenCalledTimes(1);
  });
});