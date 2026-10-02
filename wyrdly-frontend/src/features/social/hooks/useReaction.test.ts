import { renderHook, act } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { useReaction } from "./useReaction";
import type { ReactPostResponse } from "../../../api/posts";

vi.mock("../../../api/posts", () => ({
  postsApi: {
    react: vi.fn(),
  },
}));

import { postsApi } from "../../../api/posts";

const mockedReact = vi.mocked(postsApi.react);

const likeAddedResponse: ReactPostResponse = {
  status: "ADDED",
  reactionType: "LIKE",
  totalReactions: 1,
};

const loveUpdatedResponse: ReactPostResponse = {
  status: "UPDATED",
  reactionType: "LOVE",
  totalReactions: 1,
};

describe("useReaction", () => {
  beforeEach(() => {
    mockedReact.mockReset();
  });

  it("starts with isPending false and no error for an unseen postId", () => {
    const { result } = renderHook(() => useReaction());

    expect(result.current.isPending("pst_1")).toBe(false);
    expect(result.current.getError("pst_1")).toBeNull();
  });

  it("calls postsApi.react with postId, type, and an AbortSignal", async () => {
    mockedReact.mockResolvedValueOnce(likeAddedResponse);

    const { result } = renderHook(() => useReaction());

    await act(async () => {
      await result.current.react("pst_1", "LIKE");
    });

    expect(mockedReact).toHaveBeenCalledTimes(1);
    expect(mockedReact).toHaveBeenCalledWith(
      "pst_1",
      "LIKE",
      expect.any(AbortSignal),
    );
  });

  it("invokes onOptimistic before the request resolves and onServerResult on success", async () => {
    const callOrder: string[] = [];
    mockedReact.mockImplementationOnce(async () => {
      callOrder.push("api-call");
      return likeAddedResponse;
    });

    const onOptimistic = vi.fn(() => callOrder.push("optimistic"));
    const onServerResult = vi.fn(() => callOrder.push("server-result"));

    const { result } = renderHook(() => useReaction());

    await act(async () => {
      await result.current.react("pst_1", "LIKE", {
        onOptimistic,
        onServerResult,
      });
    });

    expect(onOptimistic).toHaveBeenCalledTimes(1);
    expect(onOptimistic).toHaveBeenCalledWith("LIKE");
    expect(onServerResult).toHaveBeenCalledTimes(1);
    expect(onServerResult).toHaveBeenCalledWith(likeAddedResponse);
    // onOptimistic must fire before the API resolves; onServerResult after.
    expect(callOrder).toEqual(["optimistic", "api-call", "server-result"]);
  });

  it("flips isPending true while in-flight and false after success", async () => {
    let resolveApi!: (value: ReactPostResponse) => void;
    const pendingPromise = new Promise<ReactPostResponse>((resolve) => {
      resolveApi = resolve;
    });
    mockedReact.mockReturnValueOnce(pendingPromise);

    const { result } = renderHook(() => useReaction());

    act(() => {
      void result.current.react("pst_1", "LIKE");
    });

    expect(result.current.isPending("pst_1")).toBe(true);
    expect(result.current.getError("pst_1")).toBeNull();

    resolveApi(likeAddedResponse);

    await act(async () => {
      await pendingPromise;
    });

    expect(result.current.isPending("pst_1")).toBe(false);
    expect(result.current.getError("pst_1")).toBeNull();
  });

  it("invokes onRollback and stores the error message when the API fails", async () => {
    mockedReact.mockRejectedValueOnce(new Error("Network error"));

    const onRollback = vi.fn();
    const { result } = renderHook(() => useReaction());

    await act(async () => {
      await result.current.react("pst_1", "LIKE", { onRollback });
    });

    expect(onRollback).toHaveBeenCalledTimes(1);
    expect(result.current.getError("pst_1")).toBe("Network error");
    expect(result.current.isPending("pst_1")).toBe(false);
  });

  it("does NOT invoke onRollback when the error is a cancellation", async () => {
    // axios.isCancel returns true for objects with __CANCEL__ = true.
    // We construct a minimal stand-in since axios is not mocked here.
    const cancelError = Object.assign(new Error("canceled"), {
      __CANCEL__: true,
      name: "CanceledError",
    });
    mockedReact.mockRejectedValueOnce(cancelError);

    const onRollback = vi.fn();
    const { result } = renderHook(() => useReaction());

    await act(async () => {
      await result.current.react("pst_1", "LIKE", { onRollback });
    });

    expect(onRollback).not.toHaveBeenCalled();
    // Cancellation leaves state clean.
    expect(result.current.getError("pst_1")).toBeNull();
    expect(result.current.isPending("pst_1")).toBe(false);
  });

  it("issues a second API call when react() is invoked again for the same post", async () => {
    mockedReact.mockResolvedValueOnce(likeAddedResponse);
    mockedReact.mockResolvedValueOnce(loveUpdatedResponse);

    const { result } = renderHook(() => useReaction());

    await act(async () => {
      await result.current.react("pst_1", "LIKE");
    });
    await act(async () => {
      await result.current.react("pst_1", "LOVE");
    });

    expect(mockedReact).toHaveBeenCalledTimes(2);
    expect(mockedReact).toHaveBeenNthCalledWith(
      1,
      "pst_1",
      "LIKE",
      expect.any(AbortSignal),
    );
    expect(mockedReact).toHaveBeenNthCalledWith(
      2,
      "pst_1",
      "LOVE",
      expect.any(AbortSignal),
    );
  });

  it("tracks isPending per postId independently", async () => {
    let resolveP1!: (value: ReactPostResponse) => void;
    const p1Promise = new Promise<ReactPostResponse>((resolve) => {
      resolveP1 = resolve;
    });
    mockedReact.mockReturnValueOnce(p1Promise);
    mockedReact.mockResolvedValueOnce(likeAddedResponse);

    const { result } = renderHook(() => useReaction());

    // Start pst_1 (pending) and immediately complete pst_2.
    act(() => {
      void result.current.react("pst_1", "LIKE");
    });
    await act(async () => {
      await result.current.react("pst_2", "LIKE");
    });

    expect(result.current.isPending("pst_1")).toBe(true);
    expect(result.current.isPending("pst_2")).toBe(false);

    resolveP1(likeAddedResponse);
    await act(async () => {
      await p1Promise;
    });

    expect(result.current.isPending("pst_1")).toBe(false);
  });
});
