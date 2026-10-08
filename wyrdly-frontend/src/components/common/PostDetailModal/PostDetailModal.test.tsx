import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { BrowserRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { PostDetailModal } from "./PostDetailModal";
import { postsApi } from "../../../api/posts";
import { AuthProvider } from "../../../context/AuthContext";
import type { PostApiResponse } from "../../../types/feed";

vi.mock("../../../api/posts", () => ({
  postsApi: {
    getById: vi.fn(),
    react: vi.fn(),
  },
}));

const mockedGetById = vi.mocked(postsApi.getById);

const mockPostResponse: PostApiResponse = {
  id: "pst_123",
  content: "Deep dive into federated notifications",
  mediaUrl: null,
  createdAt: "2026-10-08T12:00:00Z",
  author: {
    id: "usr_alice",
    username: "alice",
    fullName: "Alice Cooper",
    avatarUrl: "https://example.com/avatar.jpg",
  },
  reactionCounts: {
    likeCount: 5,
    loveCount: 3,
    celebrateCount: 1,
  },
  userReaction: "LIKE",
  commentsCount: 2,
};

function renderModal(
  props: Partial<Parameters<typeof PostDetailModal>[0]> = {},
) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  return render(
    <AuthProvider>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <PostDetailModal
            postId="pst_123"
            isOpen={true}
            onClose={vi.fn()}
            {...props}
          />
        </BrowserRouter>
      </QueryClientProvider>
    </AuthProvider>,
  );
}

describe("PostDetailModal Component", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("does not render when isOpen is false", () => {
    renderModal({ isOpen: false });

    expect(screen.queryByTestId("post-detail-modal")).not.toBeInTheDocument();
  });

  it("does not render when postId is null", () => {
    renderModal({ postId: null });

    expect(screen.queryByTestId("post-detail-modal")).not.toBeInTheDocument();
  });

  it("renders loading skeleton while post data is being fetched", async () => {
    mockedGetById.mockReturnValueOnce(new Promise(() => {}));

    renderModal();

    expect(screen.getByTestId("post-detail-modal")).toBeInTheDocument();
    expect(screen.getByTestId("post-modal-loading")).toBeInTheDocument();
    expect(screen.queryByTestId("post-modal-content")).not.toBeInTheDocument();
  });

  it("renders post details, author, metrics, and comments once loaded", async () => {
    mockedGetById.mockResolvedValueOnce(mockPostResponse);

    renderModal();

    await waitFor(() => {
      expect(screen.getByTestId("post-modal-content")).toBeInTheDocument();
    });

    expect(
      screen.getByText("Deep dive into federated notifications"),
    ).toBeInTheDocument();
    expect(screen.getByText("Alice Cooper")).toBeInTheDocument();
    expect(screen.getByText("@alice • wyrdly.app")).toBeInTheDocument();
    expect(screen.getByTestId("comment-section-pst_123")).toBeInTheDocument();
  });

  it("renders error state when fetch fails and allows retrying", async () => {
    mockedGetById.mockRejectedValueOnce(new Error("Network Error"));

    renderModal();

    await waitFor(() => {
      expect(screen.getByTestId("post-modal-error")).toBeInTheDocument();
    });

    expect(screen.getByText("Post unavailable")).toBeInTheDocument();
    expect(screen.getByText("Network Error")).toBeInTheDocument();

    // Now clicking retry
    mockedGetById.mockResolvedValueOnce(mockPostResponse);
    fireEvent.click(screen.getByTestId("post-modal-retry"));

    await waitFor(() => {
      expect(screen.getByTestId("post-modal-content")).toBeInTheDocument();
    });
  });

  it("calls onClose when clicking the close button", async () => {
    mockedGetById.mockResolvedValueOnce(mockPostResponse);
    const handleClose = vi.fn();

    renderModal({ onClose: handleClose });

    await waitFor(() => {
      expect(screen.getByTestId("post-modal-content")).toBeInTheDocument();
    });

    const closeBtn = screen.getByTestId("post-detail-modal-close");
    fireEvent.click(closeBtn);

    expect(handleClose).toHaveBeenCalledTimes(1);
  });

  it("calls onClose when clicking the backdrop overlay", async () => {
    mockedGetById.mockResolvedValueOnce(mockPostResponse);
    const handleClose = vi.fn();

    renderModal({ onClose: handleClose });

    await waitFor(() => {
      expect(screen.getByTestId("post-modal-content")).toBeInTheDocument();
    });

    const overlay = screen.getByTestId("post-detail-modal-overlay");
    fireEvent.click(overlay);

    expect(handleClose).toHaveBeenCalledTimes(1);
  });

  it("calls onClose when pressing the Escape key", async () => {
    mockedGetById.mockResolvedValueOnce(mockPostResponse);
    const handleClose = vi.fn();

    renderModal({ onClose: handleClose });

    await waitFor(() => {
      expect(screen.getByTestId("post-modal-content")).toBeInTheDocument();
    });

    fireEvent.keyDown(window, { key: "Escape" });

    expect(handleClose).toHaveBeenCalledTimes(1);
  });
});
