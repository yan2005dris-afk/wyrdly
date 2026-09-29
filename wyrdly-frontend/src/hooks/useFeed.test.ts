import { renderHook, act } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { useFeed } from "./useFeed";
import type { Post } from "../types/feed";

function buildPost(id: string, content: string): Post {
  return {
    id,
    author: {
      id: "user-1",
      username: "alice",
      fullName: "Alice Chen",
      avatarUrl: undefined,
      isVerified: false,
      instanceUrl: "wyrdly.app",
      stats: { followersCount: 0, followingCount: 0, postsCount: 0 },
    },
    content,
    createdAt: "2026-01-15T10:00:00Z",
    attachments: [],
    reactions: { LIKE: 0, LOVE: 0, CELEBRATE: 0, RETWEET: 0 },
    commentsCount: 0,
    visibility: "PUBLIC",
  };
}

describe("useFeed", () => {
  it("starts with an empty list, isLoading=false and error=null", () => {
    const { result } = renderHook(() => useFeed());

    expect(result.current.posts).toEqual([]);
    expect(result.current.isLoading).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it("prepends a single post via addPost", () => {
    const { result } = renderHook(() => useFeed());
    const post = buildPost("post-1", "first");

    act(() => {
      result.current.addPost(post);
    });

    expect(result.current.posts).toHaveLength(1);
    expect(result.current.posts[0]).toEqual(post);
  });

  it("keeps the most recent post first across multiple addPost calls", () => {
    const { result } = renderHook(() => useFeed());

    act(() => {
      result.current.addPost(buildPost("post-1", "first"));
    });
    act(() => {
      result.current.addPost(buildPost("post-2", "second"));
    });
    act(() => {
      result.current.addPost(buildPost("post-3", "third"));
    });

    expect(result.current.posts.map((p) => p.id)).toEqual([
      "post-3",
      "post-2",
      "post-1",
    ]);
    expect(result.current.posts[0].content).toBe("third");
    expect(result.current.posts[2].content).toBe("first");
  });

  it("refetch is a no-op and does not change the list", () => {
    const { result } = renderHook(() => useFeed());
    const post = buildPost("post-1", "first");

    act(() => {
      result.current.addPost(post);
    });

    act(() => {
      result.current.refetch();
    });

    expect(result.current.posts).toHaveLength(1);
    expect(result.current.posts[0]).toEqual(post);
  });
});
