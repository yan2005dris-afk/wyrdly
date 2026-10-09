import { useCallback, useState } from "react";
import { renderHook, act } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { useOptimisticReaction } from "./useOptimisticReaction";
import type { Post } from "../../../types/feed";
import type { ReactPostResponse } from "../../../api/posts";

vi.mock("../../../api/posts", () => ({
  postsApi: {
    react: vi.fn(),
  },
}));

import { postsApi } from "../../../api/posts";

const mockedReact = vi.mocked(postsApi.react);

function buildPost(overrides: Partial<Post> = {}): Post {
  return {
    id: "post-1",
    author: {
      id: "user-1",
      username: "alice",
      fullName: "Alice Chen",
      avatarUrl: undefined,
      isVerified: false,
      instanceUrl: "wyrdly.app",
      stats: { followersCount: 0, followingCount: 0, postsCount: 0 },
    },
    content: "hello",
    createdAt: "2026-01-15T10:00:00Z",
    attachments: [],
    reactions: { LIKE: 0, LOVE: 0, CELEBRATE: 0 },
    userReaction: undefined,
    commentsCount: 0,
    repostsCount: 0,
    isReposted: false,
    visibility: "PUBLIC",
    ...overrides,
  };
}

/** Minimal in-memory store standing in for useFeed. */
function renderWithStore(initial: readonly Post[]) {
  return renderHook(() => {
    const [posts, setPosts] = useState<readonly Post[]>(initial);
    const findPost = useCallback(
      (postId: string) => posts.find((p) => p.id === postId),
      [posts],
    );
    const updatePost = useCallback(
      (postId: string, updater: (post: Post) => Post) => {
        setPosts((prev) => prev.map((p) => (p.id === postId ? updater(p) : p)));
      },
      [],
    );
    const reaction = useOptimisticReaction({ findPost, updatePost });
    return { posts, ...reaction };
  });
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

describe("useOptimisticReaction", () => {
  beforeEach(() => {
    mockedReact.mockReset();
  });

  it("applies the optimistic move before the server responds", () => {
    const pending = deferred<ReactPostResponse>();
    mockedReact.mockReturnValueOnce(pending.promise);
    const { result } = renderWithStore([
      buildPost({
        reactions: { LIKE: 0, LOVE: 1, CELEBRATE: 0 },
        userReaction: "LOVE",
      }),
    ]);

    act(() => {
      result.current.toggle("post-1", "CELEBRATE");
    });

    expect(result.current.posts[0].userReaction).toBe("CELEBRATE");
    expect(result.current.posts[0].reactions.LOVE).toBe(0);
    expect(result.current.posts[0].reactions.CELEBRATE).toBe(1);
    expect(result.current.isPending("post-1")).toBe(true);
  });

  it("keeps the optimistic state when the server confirms the prediction", async () => {
    mockedReact.mockResolvedValueOnce({
      status: "ADDED",
      reactionType: "LIKE",
      totalReactions: 1,
    });
    const { result } = renderWithStore([buildPost()]);

    await act(async () => {
      result.current.toggle("post-1", "LIKE");
    });

    expect(result.current.posts[0].userReaction).toBe("LIKE");
    expect(result.current.posts[0].reactions.LIKE).toBe(1);
    expect(result.current.isPending("post-1")).toBe(false);
  });

  it("lets the server win when it reports a different reaction than predicted", async () => {
    // Local state is stale: the user already reacted LIKE from another tab,
    // so the client predicts ADDED but the backend toggles it off (REMOVED).
    mockedReact.mockResolvedValueOnce({
      status: "REMOVED",
      reactionType: null,
      totalReactions: 0,
    });
    const { result } = renderWithStore([buildPost()]);

    await act(async () => {
      result.current.toggle("post-1", "LIKE");
    });

    expect(result.current.posts[0].userReaction).toBeUndefined();
    expect(result.current.posts[0].reactions).toEqual({
      LIKE: 0,
      LOVE: 0,
      CELEBRATE: 0,
    });
  });

  it("clears the reaction when the server answers REMOVED", async () => {
    mockedReact.mockResolvedValueOnce({
      status: "REMOVED",
      reactionType: null,
      totalReactions: 0,
    });
    const { result } = renderWithStore([
      buildPost({
        reactions: { LIKE: 1, LOVE: 0, CELEBRATE: 0 },
        userReaction: "LIKE",
      }),
    ]);

    await act(async () => {
      result.current.toggle("post-1", "LIKE");
    });

    expect(result.current.posts[0].userReaction).toBeUndefined();
    expect(result.current.posts[0].reactions.LIKE).toBe(0);
  });

  it("restores the previous reaction and counters when the request fails", async () => {
    mockedReact.mockRejectedValueOnce(new Error("Bad Request"));
    const { result } = renderWithStore([
      buildPost({
        reactions: { LIKE: 0, LOVE: 1, CELEBRATE: 0 },
        userReaction: "LOVE",
      }),
    ]);

    await act(async () => {
      result.current.toggle("post-1", "LIKE");
    });

    expect(result.current.posts[0].userReaction).toBe("LOVE");
    expect(result.current.posts[0].reactions).toEqual({
      LIKE: 0,
      LOVE: 1,
      CELEBRATE: 0,
    });
  });

  it("does nothing when the post is not in the store", () => {
    const { result } = renderWithStore([buildPost()]);

    act(() => {
      result.current.toggle("missing", "LIKE");
    });

    expect(mockedReact).not.toHaveBeenCalled();
    expect(result.current.posts[0].userReaction).toBeUndefined();
  });
});
