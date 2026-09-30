import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("./axios", () => ({
  apiClient: {
    post: vi.fn(),
  },
}));

import { apiClient } from "./axios";
import { postsApi } from "./posts";
import type { PostApiResponse } from "../types/feed";

const mockedPost = vi.mocked(apiClient.post);

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
