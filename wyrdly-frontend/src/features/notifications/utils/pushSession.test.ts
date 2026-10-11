import { afterEach, describe, expect, it } from "vitest";
import { useAuthStore } from "../../auth/store/useAuthStore";
import { pushCopy } from "../copy";
import { assertSession, getActiveSession, makePushError } from "./pushSession";

const originalState = useAuthStore.getState();
const setUser = (user: { id: string } | null, token: string | null) => {
  useAuthStore.setState({
    user: user as never,
    token,
    isAuthenticated: Boolean(user && token),
    isLoading: false,
  });
};

afterEach(() => {
  useAuthStore.setState(originalState);
});

describe("getActiveSession", () => {
  it("returns the active userId and token when signed in", () => {
    setUser({ id: "usr_a" }, "token_a");
    expect(getActiveSession()).toEqual({ userId: "usr_a", token: "token_a" });
  });

  it("returns null when signed out", () => {
    setUser(null, null);
    expect(getActiveSession()).toBeNull();
  });

  it("returns null when token is missing", () => {
    setUser({ id: "usr_a" }, null);
    expect(getActiveSession()).toBeNull();
  });
});

describe("assertSession", () => {
  it("passes when userId and token match the active session", () => {
    setUser({ id: "usr_a" }, "token_a");
    expect(() => assertSession("usr_a", "token_a")).not.toThrow();
  });

  it("throws a tagged error when the userId changed", () => {
    setUser({ id: "usr_b" }, "token_a");
    let caught: Error | null = null;
    try {
      assertSession("usr_a", "token_a");
    } catch (err) {
      caught = err as Error;
    }
    expect(caught).not.toBeNull();
    expect(caught?.name).toBe("changedSession");
    expect(caught?.message).toBe(pushCopy.changedSession);
  });

  it("throws when the token changed for the same user", () => {
    setUser({ id: "usr_a" }, "token_b");
    expect(() => assertSession("usr_a", "token_a")).toThrow(
      /La sesión cambió/,
    );
  });

  it("throws when there is no active session", () => {
    setUser(null, null);
    expect(() => assertSession("usr_a", "token_a")).toThrow();
  });
});

describe("makePushError", () => {
  it("tags the error with the supplied PushErrorCode", () => {
    const err = makePushError("invalidVapidKey");
    expect(err).toBeInstanceOf(Error);
    expect(err.name).toBe("invalidVapidKey");
    expect(err.message).toBe(pushCopy.invalidVapidKey);
  });
});

// end of file