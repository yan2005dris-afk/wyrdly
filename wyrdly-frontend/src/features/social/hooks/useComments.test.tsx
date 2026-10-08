import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import { act, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

vi.mock("../../../api/commentsApi", () => ({
  commentsApi: {
    getComments: vi.fn(),
    createComment: vi.fn(),
    deleteComment: vi.fn(),
  },
}));

import { commentsApi } from "../../../api/commentsApi";
import { useComments } from "./useComments";
import type { Comment, CommentListResponse } from "../../../types/comments";

const mockedGet = vi.mocked(commentsApi.getComments);
const mockedCreate = vi.mocked(commentsApi.createComment);
const mockedDelete = vi.mocked(commentsApi.deleteComment);

const buildComment = (overrides: Partial<Comment> = {}): Comment => ({
  id: "cmt-1",
  postId: "post-1",
  authorId: "user-2",
  content: "Sample comment",
  createdAt: "2026-01-15T10:00:00Z",
  author: {
    id: "user-2",
    username: "bob",
    fullName: "Bob Marley",
    isVerified: false,
    instanceUrl: "wyrdly.social",
    stats: { followersCount: 0, followingCount: 0, postsCount: 0 },
  },
  ...overrides,
});

const buildListResponse = (
  overrides: Partial<CommentListResponse> = {},
): CommentListResponse => ({
  data: [buildComment()],
  totalCount: 1,
  page: 1,
  pageSize: 20,
  ...overrides,
});

const createTestQueryClient = (): QueryClient =>
  new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        gcTime: 0,
      },
    },
  });

interface CaptureWrapperOptions {
  readonly capture?: (client: QueryClient) => void;
}

const makeWrapper = (
  options: CaptureWrapperOptions = {},
): ((props: { children: ReactNode }) => React.ReactElement) => {
  const client = createTestQueryClient();
  options.capture?.(client);
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  return Wrapper;
};

describe("useComments", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("useCommentsStartsInLoadingState", async () => {
    mockedGet.mockReturnValue(new Promise(() => undefined) as never);

    const { result } = renderHook(() => useComments("post-1"), {
      wrapper: makeWrapper(),
    });

    expect(result.current.isLoading).toBe(true);
    expect(result.current.comments).toEqual([]);
    expect(result.current.totalCount).toBe(0);
    expect(result.current.isCreating).toBe(false);
    expect(result.current.isDeleting).toBe(false);
  });

  it("useCommentsFetchesCommentsOnMount", async () => {
    mockedGet.mockResolvedValueOnce(buildListResponse());

    const { result } = renderHook(() => useComments("post-1"), {
      wrapper: makeWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(mockedGet).toHaveBeenCalledWith("post-1");
    expect(result.current.comments).toHaveLength(1);
    expect(result.current.comments[0]?.id).toBe("cmt-1");
    expect(result.current.totalCount).toBe(1);
  });

  it("does not fetch when enabled=false", () => {
    renderHook(() => useComments("post-1", false), {
      wrapper: makeWrapper(),
    });

    expect(mockedGet).not.toHaveBeenCalled();
  });

  it("createCommentInvalidatesCommentsQuery", async () => {
    const created: Comment = buildComment({ id: "cmt-new", content: "Hello" });
    mockedCreate.mockResolvedValueOnce(created);

    let capturedClient: QueryClient | undefined;
    const { result } = renderHook(() => useComments("post-1"), {
      wrapper: makeWrapper({
        capture: (c) => {
          capturedClient = c;
        },
      }),
    });

    expect(typeof result.current.createComment).toBe("function");

    const invalidateSpy = vi.spyOn(capturedClient!, "invalidateQueries");

    await act(async () => {
      await result.current.createComment("Hello");
    });

    expect(mockedCreate).toHaveBeenCalledWith("post-1", "Hello");
    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: ["comments", "post-1"],
    });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["feed"] });
  });

  it("deleteCommentInvalidatesCommentsQuery", async () => {
    mockedDelete.mockResolvedValueOnce(undefined);

    let capturedClient: QueryClient | undefined;
    const { result } = renderHook(() => useComments("post-1"), {
      wrapper: makeWrapper({
        capture: (c) => {
          capturedClient = c;
        },
      }),
    });

    const invalidateSpy = vi.spyOn(capturedClient!, "invalidateQueries");

    await act(async () => {
      await result.current.deleteComment("cmt-1");
    });

    expect(mockedDelete).toHaveBeenCalledWith("post-1", "cmt-1");
    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: ["comments", "post-1"],
    });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["feed"] });
  });

  it("createCommentErrorPropagates", async () => {
    mockedGet.mockReturnValue(new Promise(() => undefined) as never);
    mockedCreate.mockRejectedValueOnce(new Error("network down"));

    const { result } = renderHook(() => useComments("post-1"), {
      wrapper: makeWrapper(),
    });

    await act(async () => {
      await Promise.resolve();
    });

    // The hook exposes the rejection so the caller (CommentInput) can
    // surface it. Verifying the rejected promise proves the failure
    // propagated end-to-end through useMutation.mutateAsync.
    await expect(result.current.createComment("x")).rejects.toThrow(
      "network down",
    );

    expect(mockedCreate).toHaveBeenCalledWith("post-1", "x");
  });
});
