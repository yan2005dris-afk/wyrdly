import { renderHook, act } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { useRepost } from "./useRepost";
import type { RepostResponse } from "../../../api/posts";

vi.mock("../../../api/posts", () => ({
  postsApi: {
    setRepost: vi.fn(),
  },
}));

import { postsApi } from "../../../api/posts";

const mockedSetRepost = vi.mocked(postsApi.setRepost);

const repostSuccessResponse: RepostResponse = {
  reposted: true,
  repostsCount: 1,
};

const unrepostSuccessResponse: RepostResponse = {
  reposted: false,
  repostsCount: 0,
};

describe("useRepost", () => {
  beforeEach(() => {
    mockedSetRepost.mockReset();
  });

  it("starts with isPending false and no error for an unseen postId", () => {
    const { result } = renderHook(() => useRepost());

    expect(result.current.isPending("pst_1")).toBe(false);
    expect(result.current.getError("pst_1")).toBeNull();
  });

  it("calls postsApi.setRepost with postId, reposted, and an AbortSignal", async () => {
    mockedSetRepost.mockResolvedValueOnce(repostSuccessResponse);

    const { result } = renderHook(() => useRepost());

    await act(async () => {
      await result.current.repost("pst_1", true);
    });

    expect(mockedSetRepost).toHaveBeenCalledTimes(1);
    expect(mockedSetRepost).toHaveBeenCalledWith(
      "pst_1",
      true,
      expect.any(AbortSignal),
    );
    expect(result.current.isPending("pst_1")).toBe(false);
    expect(result.current.getError("pst_1")).toBeNull();
  });

  it("invokes onOptimistic synchronously and onServerResult on success", async () => {
    const onOptimistic = vi.fn();
    const onServerResult = vi.fn();

    mockedSetRepost.mockResolvedValueOnce(repostSuccessResponse);

    const { result } = renderHook(() => useRepost());

    await act(async () => {
      await result.current.repost("pst_1", true, {
        onOptimistic,
        onServerResult,
      });
    });

    expect(onOptimistic).toHaveBeenCalledWith(true);
    expect(onServerResult).toHaveBeenCalledWith(repostSuccessResponse);
  });

  it("invokes onRollback and records error on failure", async () => {
    const onRollback = vi.fn();
    mockedSetRepost.mockRejectedValueOnce(new Error("Network failure"));

    const { result } = renderHook(() => useRepost());

    await act(async () => {
      await result.current.repost("pst_1", true, { onRollback });
    });

    expect(onRollback).toHaveBeenCalledTimes(1);
    expect(result.current.isPending("pst_1")).toBe(false);
    expect(result.current.getError("pst_1")).toBe("Network failure");
  });

  it("cancels prior in-flight request when a new repost call is triggered for same post", async () => {
    let firstSignal: AbortSignal | undefined;
    mockedSetRepost.mockImplementationOnce(async (_id, _rep, signal) => {
      firstSignal = signal;
      return new Promise((resolve) =>
        setTimeout(() => resolve(repostSuccessResponse), 100),
      );
    });
    mockedSetRepost.mockResolvedValueOnce(unrepostSuccessResponse);

    const { result } = renderHook(() => useRepost());

    let p1: Promise<void>;
    act(() => {
      p1 = result.current.repost("pst_1", true);
    });

    await act(async () => {
      await result.current.repost("pst_1", false);
    });

    await act(async () => {
      await p1!;
    });

    expect(firstSignal?.aborted).toBe(true);
  });
});
