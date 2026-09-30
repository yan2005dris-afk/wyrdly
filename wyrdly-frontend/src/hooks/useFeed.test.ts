import { renderHook, act, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { useFeed } from "./useFeed";
import type { Post, PostApiResponse } from "../types/feed";
import * as postsModule from "../api/posts";

vi.mock("../api/posts", () => ({
  postsApi: {
    getFeed: vi.fn(),
  },
}));

const mockedPostsApi = vi.mocked(postsModule.postsApi);

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
    reactions: { LIKE: 5, LOVE: 2, CELEBRATE: 1, RETWEET: 0 },
    userReaction: undefined,
    commentsCount: 0,
    visibility: "PUBLIC",
  };
}

function buildPostApiResponse(id: string, content: string): PostApiResponse {
  return {
    id,
    content,
    mediaUrl: null,
    createdAt: "2026-01-15T10:00:00Z",
    author: {
      id: "user-1",
      username: "alice",
      fullName: "Alice Chen",
      avatarUrl: null,
    },
    reactionCounts: {
      likeCount: 5,
      loveCount: 2,
      celebrateCount: 1,
    },
    userReaction: null,
  };
}

describe("useFeed", () => {
  beforeEach(() => {
    mockedPostsApi.getFeed.mockReset();
  });

  it("loads feed from API on mount and displays posts with reactions", async () => {
    const apiResponse = {
      data: [buildPostApiResponse("post-1", "first")],
      meta: {
        page: 1,
        pageSize: 20,
        totalElements: 1,
        totalPages: 1,
        hasNext: false,
      },
    };
    mockedPostsApi.getFeed.mockResolvedValueOnce(apiResponse);

    const { result } = renderHook(() => useFeed());

    await waitFor(() => {
      expect(result.current.posts).toHaveLength(1);
    });

    expect(mockedPostsApi.getFeed).toHaveBeenCalledWith(1, 20);
    expect(result.current.posts[0].id).toBe("post-1");
    expect(result.current.posts[0].content).toBe("first");
    expect(result.current.posts[0].reactions).toEqual({
      LIKE: 5,
      LOVE: 2,
      CELEBRATE: 1,
      RETWEET: 0,
    });
    expect(result.current.isLoading).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it("sets error when getFeed fails", async () => {
    mockedPostsApi.getFeed.mockRejectedValueOnce(new Error("Network error"));

    const { result } = renderHook(() => useFeed());

    await waitFor(() => {
      expect(result.current.error).toBe("Failed to load feed");
    });

    expect(result.current.posts).toHaveLength(0);
  });

  it("prepends a single post via addPost without clearing fetched posts", async () => {
    const apiResponse = {
      data: [buildPostApiResponse("post-1", "from api")],
      meta: {
        page: 1,
        pageSize: 20,
        totalElements: 1,
        totalPages: 1,
        hasNext: false,
      },
    };
    mockedPostsApi.getFeed.mockResolvedValueOnce(apiResponse);

    const { result } = renderHook(() => useFeed());

    await waitFor(() => {
      expect(result.current.posts).toHaveLength(1);
    });

    const newPost = buildPost("post-2", "optimistic");

    act(() => {
      result.current.addPost(newPost);
    });

    expect(result.current.posts).toHaveLength(2);
    expect(result.current.posts[0].id).toBe("post-2");
    expect(result.current.posts[1].id).toBe("post-1");
  });

  it("keeps the most recent post first across multiple addPost calls", () => {
    mockedPostsApi.getFeed.mockResolvedValueOnce({
      data: [],
      meta: {
        page: 1,
        pageSize: 20,
        totalElements: 0,
        totalPages: 0,
        hasNext: false,
      },
    });

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

  it("refetch loads posts from the API for a specific page", async () => {
    mockedPostsApi.getFeed.mockResolvedValueOnce({
      data: [],
      meta: {
        page: 1,
        pageSize: 20,
        totalElements: 0,
        totalPages: 0,
        hasNext: false,
      },
    });

    const { result } = renderHook(() => useFeed());

    await waitFor(() => {
      expect(mockedPostsApi.getFeed).toHaveBeenCalledWith(1, 20);
    });

    const page2Response = {
      data: [buildPostApiResponse("post-x", "page 2 post")],
      meta: {
        page: 2,
        pageSize: 20,
        totalElements: 25,
        totalPages: 2,
        hasNext: false,
      },
    };
    mockedPostsApi.getFeed.mockResolvedValueOnce(page2Response);

    act(() => {
      result.current.refetch(2);
    });

    await waitFor(() => {
      expect(mockedPostsApi.getFeed).toHaveBeenCalledWith(2, 20);
    });

    expect(result.current.posts).toHaveLength(1);
    expect(result.current.posts[0].id).toBe("post-x");
  });

  it("replacePost updates a post by id while preserving others", async () => {
    mockedPostsApi.getFeed.mockResolvedValueOnce({
      data: [
        buildPostApiResponse("post-1", "first"),
        buildPostApiResponse("post-2", "second"),
      ],
      meta: {
        page: 1,
        pageSize: 20,
        totalElements: 2,
        totalPages: 1,
        hasNext: false,
      },
    });

    const { result } = renderHook(() => useFeed());

    await waitFor(() => {
      expect(result.current.posts).toHaveLength(2);
    });

    const updatedPost = buildPost("post-1", "first");
    const updatedWithReaction = {
      ...updatedPost,
      userReaction: "LIKE" as const,
      reactions: { ...updatedPost.reactions, LIKE: 6 },
    };

    act(() => {
      result.current.replacePost(updatedWithReaction);
    });

    expect(result.current.posts[0].id).toBe("post-1");
    expect(result.current.posts[0].reactions.LIKE).toBe(6);
    expect(result.current.posts[0].userReaction).toBe("LIKE");
    expect(result.current.posts[1].id).toBe("post-2");
  });
});
