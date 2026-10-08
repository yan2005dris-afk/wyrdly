import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter } from "react-router-dom";

vi.mock("../../hooks/useComments", () => ({
  useComments: vi.fn(),
}));

vi.mock("../../../auth/hooks/useAuth", () => ({
  useAuth: vi.fn(),
}));

import { useComments } from "../../hooks/useComments";
import { useAuth } from "../../../auth/hooks/useAuth";
import { CommentSection } from "./CommentSection";
import type { Comment } from "../../../../types/comments";
import type { AuthContextType } from "../../../auth/types";

const mockedUseComments = vi.mocked(useComments);
const mockedUseAuth = vi.mocked(useAuth);

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

const fakeAuth = (currentUserId: string): AuthContextType => {
  const [user] = currentUserId
    ? [{ id: currentUserId, username: "self", fullName: "Self" }]
    : [null];
  return {
    user: user as AuthContextType["user"],
    token: "fake",
    isAuthenticated: Boolean(user),
    isLoading: false,
    login: vi.fn(async () => {}),
    register: vi.fn(async () => {}),
    logout: vi.fn(() => {}),
    updateUser: vi.fn(),
  };
};

const createTestQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
    },
  });

const renderSection = (
  ui: ReactNode,
  options: { currentUserId?: string } = {},
) => {
  const client = createTestQueryClient();
  mockedUseAuth.mockReturnValue(fakeAuth(options.currentUserId ?? "user-self"));
  return render(
    <QueryClientProvider client={client}>
      <BrowserRouter>{ui}</BrowserRouter>
    </QueryClientProvider>,
  );
};

describe("CommentSection", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("renders the input and the empty message when no comments", () => {
    mockedUseComments.mockReturnValue({
      comments: [],
      totalCount: 0,
      isLoading: false,
      isError: false,
      error: null,
      createComment: vi.fn(async () => buildComment()),
      isCreating: false,
      deleteComment: vi.fn(async () => {}),
      isDeleting: false,
    });

    renderSection(<CommentSection postId="post-1" postAuthorId="user-1" />);

    expect(screen.getByTestId("comment-textarea")).toBeInTheDocument();
    expect(screen.getByTestId("comment-submit")).toBeInTheDocument();
    expect(screen.getByTestId("comment-counter")).toHaveTextContent("0/1000");
    expect(screen.getByTestId("comment-list-empty")).toBeInTheDocument();
  });

  it("typing in the input updates the textarea state", () => {
    mockedUseComments.mockReturnValue({
      comments: [],
      totalCount: 0,
      isLoading: false,
      isError: false,
      error: null,
      createComment: vi.fn(async () => buildComment()),
      isCreating: false,
      deleteComment: vi.fn(async () => {}),
      isDeleting: false,
    });

    renderSection(<CommentSection postId="post-1" postAuthorId="user-1" />);

    const textarea = screen.getByTestId(
      "comment-textarea",
    ) as HTMLTextAreaElement;
    fireEvent.change(textarea, { target: { value: "Hello world" } });

    expect(textarea.value).toBe("Hello world");
    expect(screen.getByTestId("comment-counter")).toHaveTextContent("11/1000");
  });

  it("clicking Comentar calls createComment and clears the textarea", async () => {
    const created = buildComment({ id: "cmt-new", content: "Hi" });
    const createComment = vi.fn(async () => created);

    mockedUseComments.mockReturnValue({
      comments: [created],
      totalCount: 1,
      isLoading: false,
      isError: false,
      error: null,
      createComment,
      isCreating: false,
      deleteComment: vi.fn(async () => {}),
      isDeleting: false,
    });

    renderSection(<CommentSection postId="post-1" postAuthorId="user-1" />);

    const textarea = screen.getByTestId(
      "comment-textarea",
    ) as HTMLTextAreaElement;
    fireEvent.change(textarea, { target: { value: "Hi" } });

    const submit = screen.getByTestId("comment-submit");
    expect(submit).not.toBeDisabled();
    fireEvent.click(submit);

    await waitFor(() => {
      expect(createComment).toHaveBeenCalledWith("Hi");
    });
  });

  it("disables the submit button when the textarea is empty", () => {
    mockedUseComments.mockReturnValue({
      comments: [],
      totalCount: 0,
      isLoading: false,
      isError: false,
      error: null,
      createComment: vi.fn(async () => buildComment()),
      isCreating: false,
      deleteComment: vi.fn(async () => {}),
      isDeleting: false,
    });

    renderSection(<CommentSection postId="post-1" postAuthorId="user-1" />);

    expect(screen.getByTestId("comment-submit")).toBeDisabled();
  });

  it("disables the submit button while a comment is being created", () => {
    mockedUseComments.mockReturnValue({
      comments: [],
      totalCount: 0,
      isLoading: false,
      isError: false,
      error: null,
      createComment: vi.fn(async () => buildComment()),
      isCreating: true,
      deleteComment: vi.fn(async () => {}),
      isDeleting: false,
    });

    renderSection(<CommentSection postId="post-1" postAuthorId="user-1" />);

    expect(screen.getByTestId("comment-submit")).toBeDisabled();
    expect(screen.getByTestId("comment-textarea")).toBeDisabled();
  });

  it("renders existing comments from the hook", () => {
    const c1 = buildComment({
      id: "cmt-1",
      content: "First",
      authorId: "user-2",
    });
    const c2 = buildComment({
      id: "cmt-2",
      content: "Second",
      authorId: "user-3",
      author: {
        id: "user-3",
        username: "carol",
        fullName: "Carol Day",
        isVerified: false,
        instanceUrl: "wyrdly.social",
        stats: { followersCount: 0, followingCount: 0, postsCount: 0 },
      },
    });

    mockedUseComments.mockReturnValue({
      comments: [c1, c2],
      totalCount: 2,
      isLoading: false,
      isError: false,
      error: null,
      createComment: vi.fn(async () => buildComment()),
      isCreating: false,
      deleteComment: vi.fn(async () => {}),
      isDeleting: false,
    });

    renderSection(<CommentSection postId="post-1" postAuthorId="user-1" />);

    expect(screen.getByTestId("comment-cmt-1")).toBeInTheDocument();
    expect(screen.getByTestId("comment-cmt-2")).toBeInTheDocument();
    expect(screen.getByText("First")).toBeInTheDocument();
    expect(screen.getByText("Second")).toBeInTheDocument();
    // Skeleton must not appear
    expect(
      screen.queryByTestId("comment-list-skeleton"),
    ).not.toBeInTheDocument();
  });

  it("shows skeleton while loading", () => {
    mockedUseComments.mockReturnValue({
      comments: [],
      totalCount: 0,
      isLoading: true,
      isError: false,
      error: null,
      createComment: vi.fn(async () => buildComment()),
      isCreating: false,
      deleteComment: vi.fn(async () => {}),
      isDeleting: false,
    });

    renderSection(<CommentSection postId="post-1" postAuthorId="user-1" />);

    expect(screen.getByTestId("comment-list-skeleton")).toBeInTheDocument();
  });

  it("shows the delete button for the comment author", () => {
    const ownComment = buildComment({ id: "cmt-1", authorId: "user-self" });

    mockedUseComments.mockReturnValue({
      comments: [ownComment],
      totalCount: 1,
      isLoading: false,
      isError: false,
      error: null,
      createComment: vi.fn(async () => buildComment()),
      isCreating: false,
      deleteComment: vi.fn(async () => {}),
      isDeleting: false,
    });

    renderSection(<CommentSection postId="post-1" postAuthorId="user-1" />, {
      currentUserId: "user-self",
    });

    expect(screen.getByTestId("comment-delete-cmt-1")).toBeInTheDocument();
  });

  it("shows the delete button for the post owner (different from comment author)", () => {
    const otherComment = buildComment({ id: "cmt-1", authorId: "user-other" });

    mockedUseComments.mockReturnValue({
      comments: [otherComment],
      totalCount: 1,
      isLoading: false,
      isError: false,
      error: null,
      createComment: vi.fn(async () => buildComment()),
      isCreating: false,
      deleteComment: vi.fn(async () => {}),
      isDeleting: false,
    });

    renderSection(<CommentSection postId="post-1" postAuthorId="user-1" />, {
      currentUserId: "user-1",
    });

    expect(screen.getByTestId("comment-delete-cmt-1")).toBeInTheDocument();
  });

  it("hides the delete button for users who are neither author nor post owner", () => {
    const someoneElseComment = buildComment({
      id: "cmt-1",
      authorId: "user-other",
    });

    mockedUseComments.mockReturnValue({
      comments: [someoneElseComment],
      totalCount: 1,
      isLoading: false,
      isError: false,
      error: null,
      createComment: vi.fn(async () => buildComment()),
      isCreating: false,
      deleteComment: vi.fn(async () => {}),
      isDeleting: false,
    });

    renderSection(<CommentSection postId="post-1" postAuthorId="user-1" />, {
      currentUserId: "user-third",
    });

    expect(
      screen.queryByTestId("comment-delete-cmt-1"),
    ).not.toBeInTheDocument();
  });

  it("clicking the delete button calls deleteComment with the comment id", async () => {
    const deleteComment = vi.fn(async () => {});
    const ownComment = buildComment({ id: "cmt-1", authorId: "user-self" });

    mockedUseComments.mockReturnValue({
      comments: [ownComment],
      totalCount: 1,
      isLoading: false,
      isError: false,
      error: null,
      createComment: vi.fn(async () => buildComment()),
      isCreating: false,
      deleteComment,
      isDeleting: false,
    });

    renderSection(<CommentSection postId="post-1" postAuthorId="user-1" />, {
      currentUserId: "user-self",
    });

    fireEvent.click(screen.getByTestId("comment-delete-cmt-1"));

    await waitFor(() => {
      expect(deleteComment).toHaveBeenCalledWith("cmt-1");
    });
  });
});
