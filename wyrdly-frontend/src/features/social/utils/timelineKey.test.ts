import { describe, it, expect } from "vitest";
import { getTimelineItemKey } from "./timelineKey";

describe("getTimelineItemKey", () => {
  it("uses the post id for original publications", () => {
    expect(getTimelineItemKey({ id: "post-1" })).toBe("post-1");
  });

  it("namespaces shares by reposter so they never collide with the original", () => {
    expect(
      getTimelineItemKey({
        id: "post-1",
        repostContext: {
          reposterId: "user-2",
          reposterUsername: "bob",
          reposterName: "Bob",
          repostedAt: "2026-10-01T10:00:00Z",
        },
      }),
    ).toBe("repost:user-2:post-1");
  });
});
