import { describe, it, expect, beforeEach } from "vitest";
import { getAccessToken, setAccessToken } from "./tokenStore";

describe("tokenStore", () => {
  beforeEach(() => {
    setAccessToken(null);
  });

  it("initializes with null", () => {
    expect(getAccessToken()).toBeNull();
  });

  it("stores and retrieves access token in memory", () => {
    setAccessToken("test-token-xyz");
    expect(getAccessToken()).toBe("test-token-xyz");
  });

  it("clears access token when set to null", () => {
    setAccessToken("test-token-xyz");
    setAccessToken(null);
    expect(getAccessToken()).toBeNull();
  });
});
