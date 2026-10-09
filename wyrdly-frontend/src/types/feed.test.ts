import { describe, it, expect } from "vitest";
import { mapPostApiResponseToPost, type PostApiResponse } from "./feed";

function buildResponse(
  reactionCounts: PostApiResponse["reactionCounts"],
): PostApiResponse {
  return {
    id: "post-1",
    content: "hello",
    mediaUrl: null,
    createdAt: "2026-01-15T10:00:00Z",
    author: {
      id: "user-1",
      username: "alice",
      fullName: "Alice Chen",
      avatarUrl: null,
    },
    reactionCounts,
    userReaction: null,
  };
}

describe("mapPostApiResponseToPost", () => {
  it("maps per-type reaction counts", () => {
    const post = mapPostApiResponseToPost(
      buildResponse({ likeCount: 3, loveCount: 2, celebrateCount: 1 }),
    );

    expect(post.reactions).toEqual({
      LIKE: 3,
      LOVE: 2,
      CELEBRATE: 1,
    });
    expect(post.repostsCount).toBe(0);
    expect(post.isReposted).toBe(false);
  });

  it("maps repostsCount and userHasReposted when provided", () => {
    const apiResponse: PostApiResponse = {
      ...buildResponse({ likeCount: 0, loveCount: 0, celebrateCount: 0 }),
      repostsCount: 12,
      userHasReposted: true,
    };

    const post = mapPostApiResponseToPost(apiResponse);

    expect(post.repostsCount).toBe(12);
    expect(post.isReposted).toBe(true);
  });
});

describe("mapPostApiResponseToPost — repostContext (HU #150)", () => {
  const base = buildResponse({ likeCount: 0, loveCount: 0, celebrateCount: 0 });

  it("leaves repostContext undefined when the backend omits it", () => {
    expect(mapPostApiResponseToPost(base).repostContext).toBeUndefined();
  });

  it("leaves repostContext undefined when the backend sends null", () => {
    const post = mapPostApiResponseToPost({ ...base, repostContext: null });
    expect(post.repostContext).toBeUndefined();
  });

  it("maps the repost context and keeps the original author", () => {
    const post = mapPostApiResponseToPost({
      ...base,
      repostContext: {
        reposterId: "user-2",
        reposterUsername: "bob",
        reposterName: "Bob Smith",
        reposterAvatarUrl: null,
        repostedAt: "2026-01-16T09:00:00Z",
      },
    });

    expect(post.author.username).toBe("alice");
    expect(post.repostContext).toEqual({
      reposterId: "user-2",
      reposterUsername: "bob",
      reposterName: "Bob Smith",
      reposterAvatarUrl: undefined,
      repostedAt: "2026-01-16T09:00:00Z",
    });
  });
});
