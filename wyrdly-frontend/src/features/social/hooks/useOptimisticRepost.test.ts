import { renderHook, act } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { useState, useCallback } from "react";
import { useOptimisticRepost } from "./useOptimisticRepost";
import type { Post } from "../../../types/feed";
import type { RepostResponse } from "../../../api/posts";

vi.mock("../../../api/posts", () => ({
  postsApi: {
    setRepost: vi.fn(),
  },
}));

import { postsApi } from "../../../api/posts";

const mockedSetRepost = vi.mocked(postsApi.setRepost);

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
    const repost = useOptimisticRepost({ findPost, updatePost });
    return { posts, ...repost };
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

describe("useOptimisticRepost", () => {
  beforeEach(() => {
    mockedSetRepost.mockReset();
  });

  it("applies optimistic update (+1 count, isReposted true) before server responds", () => {
    const pending = deferred<RepostResponse>();
    mockedSetRepost.mockReturnValueOnce(pending.promise);

    const { result } = renderWithStore([
      buildPost({ repostsCount: 3, isReposted: false }),
    ]);

    act(() => {
      result.current.toggle("post-1");
    });

    expect(result.current.posts[0].isReposted).toBe(true);
    expect(result.current.posts[0].repostsCount).toBe(4);
    expect(result.current.isPending("post-1")).toBe(true);
  });

  it("reconciles state when server confirms the operation", async () => {
    mockedSetRepost.mockResolvedValueOnce({
      reposted: true,
      repostsCount: 4,
    });

    const { result } = renderWithStore([
      buildPost({ repostsCount: 3, isReposted: false }),
    ]);

    await act(async () => {
      result.current.toggle("post-1");
    });

    expect(result.current.posts[0].isReposted).toBe(true);
    expect(result.current.posts[0].repostsCount).toBe(4);
    expect(result.current.isPending("post-1")).toBe(false);
  });

  it("applies optimistic decrement when toggling off", async () => {
    mockedSetRepost.mockResolvedValueOnce({
      reposted: false,
      repostsCount: 3,
    });

    const { result } = renderWithStore([
      buildPost({ repostsCount: 4, isReposted: true }),
    ]);

    await act(async () => {
      result.current.toggle("post-1");
    });

    expect(result.current.posts[0].isReposted).toBe(false);
    expect(result.current.posts[0].repostsCount).toBe(3);
  });

  it("rolls back to previous state if request fails", async () => {
    mockedSetRepost.mockRejectedValueOnce(new Error("Server error"));

    const { result } = renderWithStore([
      buildPost({ repostsCount: 3, isReposted: false }),
    ]);

    await act(async () => {
      result.current.toggle("post-1");
    });

    expect(result.current.posts[0].isReposted).toBe(false);
    expect(result.current.posts[0].repostsCount).toBe(3);
  });

  it("does nothing when the post is not found in store", () => {
    const { result } = renderWithStore([buildPost()]);

    act(() => {
      result.current.toggle("missing-post");
    });

    expect(mockedSetRepost).not.toHaveBeenCalled();
  });
});
