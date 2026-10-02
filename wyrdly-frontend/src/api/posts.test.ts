import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("./axios", () => ({
  apiClient: {
    post: vi.fn(),
    get: vi.fn(),
  },
}));

import { apiClient } from "./axios";
import { postsApi, type ReactPostResponse } from "./posts";
import type { PostApiResponse } from "../types/feed";

const mockedPost = vi.mocked(apiClient.post);
const mockedGet = vi.mocked(apiClient.get);

const successResponse: PostApiResponse = {
  id: "post-abc",
  content: "Hello federated network!",
  mediaUrl: null,
  createdAt: "2026-01-15T10:00:00Z",
  author: {
    id: "user-1",
    username: "alice",
    fullName: "Alice Chen",
    avatarUrl: null,
  },
  reactionCounts: {
    likeCount: 0,
    loveCount: 0,
    celebrateCount: 0,
  },
  userReaction: null,
};

describe("postsApi.create", () => {
  beforeEach(() => {
    mockedPost.mockReset();
  });

  it("POSTs to /api/posts with content and a null mediaUrl when no media is attached", async () => {
    mockedPost.mockResolvedValueOnce({ data: successResponse });

    const result = await postsApi.create({
      content: "Hello federated network!",
    });

    expect(mockedPost).toHaveBeenCalledTimes(1);
    expect(mockedPost).toHaveBeenCalledWith("/api/posts", {
      content: "Hello federated network!",
      mediaUrl: null,
    });
    expect(result).toEqual(successResponse);
  });

  it("includes the mediaUrl in the request body when provided", async () => {
    const responseWithMedia: PostApiResponse = {
      ...successResponse,
      mediaUrl: "https://example.com/x.jpg",
    };
    mockedPost.mockResolvedValueOnce({ data: responseWithMedia });

    const result = await postsApi.create({
      content: "with attachment",
      mediaUrl: "https://example.com/x.jpg",
    });

    expect(mockedPost).toHaveBeenCalledWith("/api/posts", {
      content: "with attachment",
      mediaUrl: "https://example.com/x.jpg",
    });
    expect(result.mediaUrl).toBe("https://example.com/x.jpg");
  });
});

describe("postsApi.getFeed", () => {
  beforeEach(() => {
    mockedGet.mockReset();
  });

  it("calls /api/feed with page and pageSize", async () => {
    mockedGet.mockResolvedValueOnce({
      data: {
        data: [],
        meta: {
          page: 1,
          pageSize: 20,
          totalElements: 0,
          totalPages: 0,
          hasNext: false,
        },
      },
    });

    await postsApi.getFeed(2, 10);

    expect(mockedGet).toHaveBeenCalledWith("/api/feed", {
      params: { page: 2, pageSize: 10 },
    });
  });

  it("calls /api/feed with cursor and limit when options object is passed", async () => {
    mockedGet.mockResolvedValueOnce({
      data: {
        data: [],
        meta: {
          page: 1,
          pageSize: 20,
          totalElements: 0,
          totalPages: 0,
          hasNext: false,
          nextCursor: "abc",
          hasMore: true,
        },
      },
    });

    await postsApi.getFeed({ cursor: "abc", limit: 15 });

    expect(mockedGet).toHaveBeenCalledWith("/api/feed", {
      params: {
        cursor: "abc",
        limit: 15,
        page: undefined,
        pageSize: undefined,
      },
    });
  });
});

describe("postsApi.react (HU09)", () => {
  beforeEach(() => {
    mockedPost.mockReset();
  });

  const baseReactResponse: ReactPostResponse = {
    status: "ADDED",
    reactionType: "LIKE",
    totalReactions: 1,
  };

  it("POSTs to /api/posts/{postId}/react with the type in the body", async () => {
    mockedPost.mockResolvedValueOnce({ data: baseReactResponse });

    const result = await postsApi.react("post-abc", "LIKE");

    expect(mockedPost).toHaveBeenCalledTimes(1);
    expect(mockedPost).toHaveBeenCalledWith(
      "/api/posts/post-abc/react",
      { type: "LIKE" },
      expect.objectContaining({
        headers: expect.objectContaining({
          "Idempotency-Key": expect.any(String) as string,
        }),
      }),
    );
    expect(result).toEqual(baseReactResponse);
  });

  it("forwards an AbortSignal in the axios config when provided", async () => {
    mockedPost.mockResolvedValueOnce({ data: baseReactResponse });
    const controller = new AbortController();

    await postsApi.react("post-abc", "LOVE", controller.signal);

    const callArgs = mockedPost.mock.calls[0];
    expect(callArgs).toBeDefined();
    const config = callArgs?.[2] as { signal?: AbortSignal } | undefined;
    expect(config?.signal).toBe(controller.signal);
  });

  it("sends a fresh Idempotency-Key on every call", async () => {
    mockedPost.mockResolvedValueOnce({ data: baseReactResponse });
    mockedPost.mockResolvedValueOnce({
      data: { ...baseReactResponse, totalReactions: 2 },
    });

    await postsApi.react("post-abc", "LIKE");
    await postsApi.react("post-abc", "LIKE");

    const firstCall = mockedPost.mock.calls[0];
    const secondCall = mockedPost.mock.calls[1];
    const firstKey = (firstCall?.[2] as { headers?: Record<string, string> })
      ?.headers?.["Idempotency-Key"];
    const secondKey = (secondCall?.[2] as { headers?: Record<string, string> })
      ?.headers?.["Idempotency-Key"];

    expect(firstKey).toBeDefined();
    expect(secondKey).toBeDefined();
    expect(firstKey).not.toBe(secondKey);
  });

  it("returns the server response shape unchanged", async () => {
    const removed: ReactPostResponse = {
      status: "REMOVED",
      reactionType: null,
      totalReactions: 0,
    };
    mockedPost.mockResolvedValueOnce({ data: removed });

    const result = await postsApi.react("post-abc", "LIKE");

    expect(result.status).toBe("REMOVED");
    expect(result.reactionType).toBeNull();
    expect(result.totalReactions).toBe(0);
  });
});
