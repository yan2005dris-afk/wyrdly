import { describe, it, expect, vi, beforeEach } from "vitest";
import type {
  FollowActionResponse,
  GraphSuggestionsResponse,
} from "../types/suggestions";

vi.mock("./axios", () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

import { apiClient } from "./axios";
import { usersApi } from "./users";

const mockedGet = vi.mocked(apiClient.get);
const mockedPost = vi.mocked(apiClient.post);
const mockedDelete = vi.mocked(apiClient.delete);

describe("usersApi.getSuggestions", () => {
  beforeEach(() => {
    mockedGet.mockReset();
    mockedPost.mockReset();
    mockedDelete.mockReset();
  });

  it("hits /api/users/suggestions with default params page=0&pageSize=10", async () => {
    const responsePayload: GraphSuggestionsResponse = {
      data: [],
      meta: { page: 0, pageSize: 10, totalCount: 0 },
    };
    mockedGet.mockResolvedValueOnce({ data: responsePayload });

    const result = await usersApi.getSuggestions();

    expect(mockedGet).toHaveBeenCalledTimes(1);
    expect(mockedGet).toHaveBeenCalledWith("/api/users/suggestions", {
      params: { page: 0, pageSize: 10 },
    });
    expect(result).toEqual(responsePayload);
  });

  it("passes explicit page and pageSize through to the API client", async () => {
    const responsePayload: GraphSuggestionsResponse = {
      data: [
        {
          id: "user-1",
          username: "alice",
          fullName: "Alice Chen",
          avatarUrl: null,
          mutualConnectionSnippet: "Followed by 2 people",
          isFollowing: false,
        },
      ],
      meta: { page: 2, pageSize: 5, totalCount: 12 },
    };
    mockedGet.mockResolvedValueOnce({ data: responsePayload });

    const result = await usersApi.getSuggestions({ page: 2, pageSize: 5 });

    expect(mockedGet).toHaveBeenCalledWith("/api/users/suggestions", {
      params: { page: 2, pageSize: 5 },
    });
    expect(result.meta.page).toBe(2);
    expect(result.meta.pageSize).toBe(5);
  });
});

describe("usersApi.follow", () => {
  beforeEach(() => {
    mockedGet.mockReset();
    mockedPost.mockReset();
    mockedDelete.mockReset();
  });

  it("POSTs to /api/users/{targetUserId}/follow", async () => {
    const responsePayload: FollowActionResponse = {
      message: "Followed",
      targetUserId: "user-123",
      following: true,
    };
    mockedPost.mockResolvedValueOnce({ data: responsePayload });

    const result = await usersApi.follow("user-123");

    expect(mockedPost).toHaveBeenCalledTimes(1);
    expect(mockedPost).toHaveBeenCalledWith("/api/users/user-123/follow");
    expect(result.following).toBe(true);
    expect(result.targetUserId).toBe("user-123");
  });

  it("URL-encodes target user ids with special characters", async () => {
    const responsePayload: FollowActionResponse = {
      message: "Followed",
      targetUserId: "user/with/slash",
      following: true,
    };
    mockedPost.mockResolvedValueOnce({ data: responsePayload });

    await usersApi.follow("user/with/slash");

    expect(mockedPost).toHaveBeenCalledWith(
      "/api/users/user%2Fwith%2Fslash/follow",
    );
  });
});

describe("usersApi.unfollow", () => {
  beforeEach(() => {
    mockedGet.mockReset();
    mockedPost.mockReset();
    mockedDelete.mockReset();
  });

  it("DELETEs /api/users/{targetUserId}/follow", async () => {
    const responsePayload: FollowActionResponse = {
      message: "Unfollowed",
      targetUserId: "user-123",
      following: false,
    };
    mockedDelete.mockResolvedValueOnce({ data: responsePayload });

    const result = await usersApi.unfollow("user-123");

    expect(mockedDelete).toHaveBeenCalledTimes(1);
    expect(mockedDelete).toHaveBeenCalledWith("/api/users/user-123/follow");
    expect(result.following).toBe(false);
  });
});
