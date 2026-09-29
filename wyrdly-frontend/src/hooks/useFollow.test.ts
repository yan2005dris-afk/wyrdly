import { renderHook, act } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { usersApi } from "../api/users";
import { useFollow } from "./useFollow";
import type { FollowActionResponse } from "../types/suggestions";

vi.mock("../api/users", () => ({
  usersApi: {
    follow: vi.fn(),
    unfollow: vi.fn(),
  },
}));

const mockedFollow = vi.mocked(usersApi.follow);
const mockedUnfollow = vi.mocked(usersApi.unfollow);

const followResponse: FollowActionResponse = {
  message: "Followed",
  targetUserId: "user-1",
  following: true,
};

const unfollowResponse: FollowActionResponse = {
  message: "Unfollowed",
  targetUserId: "user-1",
  following: false,
};

describe("useFollow", () => {
  beforeEach(() => {
    mockedFollow.mockReset();
    mockedUnfollow.mockReset();
  });

  it("calls usersApi.follow and resolves", async () => {
    mockedFollow.mockResolvedValueOnce(followResponse);

    const { result } = renderHook(() => useFollow());

    await act(async () => {
      await result.current.follow("user-1");
    });

    expect(mockedFollow).toHaveBeenCalledTimes(1);
    expect(mockedFollow).toHaveBeenCalledWith("user-1");
    expect(result.current.error).toBeNull();
  });

  it("sets error and rethrows when follow fails", async () => {
    const failure = new Error("network down");
    mockedFollow.mockRejectedValueOnce(failure);

    const { result } = renderHook(() => useFollow());

    await act(async () => {
      await expect(result.current.follow("user-1")).rejects.toThrow(
        "network down",
      );
    });

    expect(result.current.error).toBe("network down");
  });

  it("uses the 'Failed to follow user' fallback when error has no message", async () => {
    mockedFollow.mockRejectedValueOnce("string-error");

    const { result } = renderHook(() => useFollow());

    await act(async () => {
      await expect(result.current.follow("user-1")).rejects.toBe(
        "string-error",
      );
    });

    expect(result.current.error).toBe("Failed to follow user");
  });

  it("reports isMutating=true during an in-flight follow call and resets after", async () => {
    let resolveFollow: ((value: FollowActionResponse) => void) | undefined;
    const pending = new Promise<FollowActionResponse>((resolve) => {
      resolveFollow = resolve;
    });
    mockedFollow.mockReturnValueOnce(pending);

    const { result } = renderHook(() => useFollow());

    let followPromise: Promise<void> | undefined;
    act(() => {
      followPromise = result.current.follow("user-1");
    });

    expect(result.current.isMutating).toBe(true);

    await act(async () => {
      resolveFollow?.(followResponse);
      await followPromise;
    });

    expect(result.current.isMutating).toBe(false);
  });

  it("calls usersApi.unfollow and resolves", async () => {
    mockedUnfollow.mockResolvedValueOnce(unfollowResponse);

    const { result } = renderHook(() => useFollow());

    await act(async () => {
      await result.current.unfollow("user-1");
    });

    expect(mockedUnfollow).toHaveBeenCalledTimes(1);
    expect(mockedUnfollow).toHaveBeenCalledWith("user-1");
    expect(result.current.error).toBeNull();
  });

  it("sets error and rethrows when unfollow fails", async () => {
    const failure = new Error("server error");
    mockedUnfollow.mockRejectedValueOnce(failure);

    const { result } = renderHook(() => useFollow());

    await act(async () => {
      await expect(result.current.unfollow("user-1")).rejects.toThrow(
        "server error",
      );
    });

    expect(result.current.error).toBe("server error");
  });

  it("uses the 'Failed to unfollow user' fallback when error has no message", async () => {
    mockedUnfollow.mockRejectedValueOnce("string-error");

    const { result } = renderHook(() => useFollow());

    await act(async () => {
      await expect(result.current.unfollow("user-1")).rejects.toBe(
        "string-error",
      );
    });

    expect(result.current.error).toBe("Failed to unfollow user");
  });

  it("clears previous error on a subsequent successful follow", async () => {
    mockedFollow.mockRejectedValueOnce(new Error("first failure"));
    mockedFollow.mockResolvedValueOnce(followResponse);

    const { result } = renderHook(() => useFollow());

    await act(async () => {
      await expect(result.current.follow("user-1")).rejects.toThrow(
        "first failure",
      );
    });
    expect(result.current.error).toBe("first failure");

    await act(async () => {
      await result.current.follow("user-1");
    });
    expect(result.current.error).toBeNull();
  });
});
