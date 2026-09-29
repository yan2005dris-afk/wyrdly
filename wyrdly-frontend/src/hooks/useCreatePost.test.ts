import { renderHook, act } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { postsApi } from "../api/posts";
import { useCreatePost } from "./useCreatePost";
import type { PostApiResponse } from "../types/feed";

vi.mock("../api/posts", () => ({
  postsApi: {
    create: vi.fn(),
  },
}));

const mockedCreate = vi.mocked(postsApi.create);

const successResponse: PostApiResponse = {
  id: "post-abc",
  content: "Hello world",
  mediaUrl: null,
  createdAt: "2026-01-15T10:00:00Z",
  author: {
    id: "user-1",
    username: "alice",
    fullName: "Alice Chen",
    avatarUrl: null,
  },
};

describe("useCreatePost", () => {
  beforeEach(() => {
    mockedCreate.mockReset();
  });

  it("returns the API response and clears error on success", async () => {
    mockedCreate.mockResolvedValueOnce(successResponse);

    const { result } = renderHook(() => useCreatePost());

    let returned: PostApiResponse | null = "sentinel" as unknown as null;
    await act(async () => {
      returned = await result.current.createPost({ content: "Hello world" });
    });

    expect(returned).toEqual(successResponse);
    expect(result.current.error).toBeNull();
    expect(result.current.isSubmitting).toBe(false);
    expect(mockedCreate).toHaveBeenCalledTimes(1);
    expect(mockedCreate).toHaveBeenCalledWith({ content: "Hello world" });
  });

  it("sets 'Failed to publish post' and returns null when the API throws", async () => {
    mockedCreate.mockRejectedValueOnce(new Error("network down"));

    const { result } = renderHook(() => useCreatePost());

    let returned: PostApiResponse | null = "sentinel" as unknown as null;
    await act(async () => {
      returned = await result.current.createPost({ content: "Hello world" });
    });

    expect(returned).toBeNull();
    expect(result.current.error).toBe("Failed to publish post");
    expect(result.current.isSubmitting).toBe(false);
  });

  it("uses the 'Failed to publish post' fallback for non-Error rejections too", async () => {
    mockedCreate.mockRejectedValueOnce("string-error");

    const { result } = renderHook(() => useCreatePost());

    await act(async () => {
      await result.current.createPost({ content: "Hello world" });
    });

    expect(result.current.error).toBe("Failed to publish post");
  });

  it("reports isSubmitting=true during an in-flight call and resets after", async () => {
    let resolveCreate: ((value: PostApiResponse) => void) | undefined;
    const pending = new Promise<PostApiResponse>((resolve) => {
      resolveCreate = resolve;
    });
    mockedCreate.mockReturnValueOnce(pending);

    const { result } = renderHook(() => useCreatePost());

    let createPromise: Promise<PostApiResponse | null> | undefined;
    act(() => {
      createPromise = result.current.createPost({ content: "Hello world" });
    });

    expect(result.current.isSubmitting).toBe(true);

    await act(async () => {
      resolveCreate?.(successResponse);
      await createPromise;
    });

    expect(result.current.isSubmitting).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it("clears a previous error on a subsequent successful create", async () => {
    mockedCreate.mockRejectedValueOnce(new Error("first failure"));
    mockedCreate.mockResolvedValueOnce(successResponse);

    const { result } = renderHook(() => useCreatePost());

    await act(async () => {
      await result.current.createPost({ content: "Hello world" });
    });
    expect(result.current.error).toBe("Failed to publish post");

    await act(async () => {
      await result.current.createPost({ content: "Hello world" });
    });
    expect(result.current.error).toBeNull();
  });

  it("forwards mediaUrl into the API payload", async () => {
    mockedCreate.mockResolvedValueOnce(successResponse);

    const { result } = renderHook(() => useCreatePost());

    await act(async () => {
      await result.current.createPost({
        content: "with media",
        mediaUrl: "https://cdn.wyrdly.app/posts/img_abc.jpg",
      });
    });

    expect(mockedCreate).toHaveBeenCalledWith({
      content: "with media",
      mediaUrl: "https://cdn.wyrdly.app/posts/img_abc.jpg",
    });
  });
});
