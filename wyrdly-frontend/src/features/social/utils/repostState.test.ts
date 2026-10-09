import { describe, it, expect } from "vitest";
import { withRepost, type RepostState } from "./repostState";

const INITIAL: RepostState = {
  repostsCount: 5,
  isReposted: false,
};

describe("withRepost", () => {
  it("increments repostsCount and sets isReposted to true when reposting", () => {
    const next = withRepost(INITIAL, true);

    expect(next.isReposted).toBe(true);
    expect(next.repostsCount).toBe(6);
  });

  it("decrements repostsCount and sets isReposted to false when unreposting", () => {
    const repostedState: RepostState = {
      repostsCount: 5,
      isReposted: true,
    };
    const next = withRepost(repostedState, false);

    expect(next.isReposted).toBe(false);
    expect(next.repostsCount).toBe(4);
  });

  it("never decrements repostsCount below 0", () => {
    const zeroState: RepostState = {
      repostsCount: 0,
      isReposted: true,
    };
    const next = withRepost(zeroState, false);

    expect(next.isReposted).toBe(false);
    expect(next.repostsCount).toBe(0);
  });

  it("returns the exact same reference when reposted status matches current state", () => {
    expect(withRepost(INITIAL, false)).toBe(INITIAL);

    const repostedState: RepostState = {
      repostsCount: 5,
      isReposted: true,
    };
    expect(withRepost(repostedState, true)).toBe(repostedState);
  });

  it("preserves other fields of the post object", () => {
    const post = {
      ...INITIAL,
      id: "post-1",
      content: "Hello world",
    };
    const next = withRepost(post, true);

    expect(next.id).toBe("post-1");
    expect(next.content).toBe("Hello world");
    expect(next.isReposted).toBe(true);
    expect(next.repostsCount).toBe(6);
  });
});
