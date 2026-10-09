import { describe, it, expect } from "vitest";
import { isHiddenFromOwnerTimeline } from "./profileTimeline";
import type { RepostContext } from "../../../types/feed";

const ownerShare: RepostContext = {
  reposterId: "owner",
  reposterUsername: "owner",
  reposterName: "Owner",
  repostedAt: "2026-10-01T10:00:00Z",
};

describe("isHiddenFromOwnerTimeline", () => {
  it("hides the owner's share once the owner undoes the repost", () => {
    expect(
      isHiddenFromOwnerTimeline(
        { isReposted: false, repostContext: ownerShare },
        "owner",
        "owner",
      ),
    ).toBe(true);
  });

  it("keeps the owner's share while it is still reposted", () => {
    expect(
      isHiddenFromOwnerTimeline(
        { isReposted: true, repostContext: ownerShare },
        "owner",
        "owner",
      ),
    ).toBe(false);
  });

  it("never hides entries when a visitor views someone else's profile", () => {
    expect(
      isHiddenFromOwnerTimeline(
        { isReposted: false, repostContext: ownerShare },
        "owner",
        "visitor",
      ),
    ).toBe(false);
  });

  it("never hides original publications", () => {
    expect(
      isHiddenFromOwnerTimeline({ isReposted: false }, "owner", "owner"),
    ).toBe(false);
  });

  it("does not hide when the owner is unknown", () => {
    expect(
      isHiddenFromOwnerTimeline(
        { isReposted: false, repostContext: ownerShare },
        undefined,
        undefined,
      ),
    ).toBe(false);
  });
});
