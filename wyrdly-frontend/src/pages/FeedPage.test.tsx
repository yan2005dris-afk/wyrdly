import {
  render,
  screen,
  fireEvent,
  waitFor,
  act,
} from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { BrowserRouter } from "react-router-dom";
import { FeedPage } from "./FeedPage";
import { AuthProvider } from "../context/AuthContext";
import { usersApi } from "../api/users";
import type {
  GraphSuggestionUser,
  GraphSuggestionsResponse,
} from "../types/suggestions";
import type { PostApiResponse } from "../types/feed";
import type { MediaUploadResponse } from "../types/media";

vi.mock("../features/social", async () => {
  const actual =
    await vi.importActual<typeof import("../features/social")>(
      "../features/social",
    );
  return {
    ...actual,
    useGraphSuggestions: vi.fn(),
    useFollow: vi.fn(),
    useCreatePost: vi.fn(),
  };
});

vi.mock("../hooks/useMediaUpload", () => ({
  useMediaUpload: vi.fn(),
}));

vi.mock("../api/users", async () => {
  const actual =
    await vi.importActual<typeof import("../api/users")>("../api/users");
  return {
    ...actual,
    usersApi: {
      ...actual.usersApi,
      follow: vi.fn(),
      unfollow: vi.fn(),
    },
  };
});

vi.mock("../api/posts", () => ({
  postsApi: {
    create: vi.fn(),
    getFeed: vi.fn(),
    react: vi.fn(),
  },
}));

import {
  useGraphSuggestions,
  useFollow,
  useCreatePost,
} from "../features/social";
import { useMediaUpload } from "../hooks/useMediaUpload";
import { postsApi } from "../api/posts";

const mockedUseGraphSuggestions = vi.mocked(useGraphSuggestions);
const mockedUseFollow = vi.mocked(useFollow);
const mockedUseCreatePost = vi.mocked(useCreatePost);
const mockedUseMediaUpload = vi.mocked(useMediaUpload);

const mockedFollow = vi.mocked(usersApi.follow);
const mockedUnfollow = vi.mocked(usersApi.unfollow);
const mockedGetFeed = vi.mocked(postsApi.getFeed);

const suggestionAlice: GraphSuggestionUser = {
  id: "user-alice",
  username: "alice",
  fullName: "Alice Chen",
  avatarUrl: "https://example.com/alice.jpg",
  mutualConnectionSnippet: "Followed by Jon and 2 others",
  isFollowing: false,
};

const suggestionMarcus: GraphSuggestionUser = {
  id: "user-marcus",
  username: "marcus",
  fullName: "Marcus Cole",
  avatarUrl: null,
  mutualConnectionSnippet: "Followed by Priya and 5 others",
  isFollowing: true,
};

const successResponse: GraphSuggestionsResponse = {
  data: [suggestionAlice, suggestionMarcus],
  meta: { page: 0, pageSize: 10, totalCount: 2 },
};

function buildCreatePostResponse(
  overrides: Partial<PostApiResponse> = {},
): PostApiResponse {
  return {
    id: "post-new",
    content: "Testing post publish",
    mediaUrl: null,
    createdAt: "2026-01-15T10:00:00Z",
    author: {
      id: "usr-current",
      username: "maya",
      fullName: "Maya Krishnan",
      avatarUrl: null,
    },
    reactionCounts: {
      likeCount: 0,
      loveCount: 0,
      celebrateCount: 0,
    },
    userReaction: null,
    ...overrides,
  };
}

function renderFeedPage() {
  return render(
    <AuthProvider>
      <BrowserRouter>
        <FeedPage />
      </BrowserRouter>
    </AuthProvider>,
  );
}

describe("FeedPage Component", () => {
  let createPostSpy: ReturnType<typeof vi.fn>;
  let uploadSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    mockedUseGraphSuggestions.mockReset();
    mockedUseFollow.mockReset();
    mockedUseCreatePost.mockReset();
    mockedUseMediaUpload.mockReset();
    mockedFollow.mockReset();
    mockedUnfollow.mockReset();
    mockedGetFeed.mockReset();

    // Default: empty feed on mount
    mockedGetFeed.mockResolvedValue({
      data: [],
      meta: {
        page: 1,
        pageSize: 20,
        totalElements: 0,
        totalPages: 0,
        hasNext: false,
      },
    });

    mockedUseGraphSuggestions.mockReturnValue({
      suggestions: successResponse.data,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });

    mockedUseFollow.mockReturnValue({
      follow: mockedFollow,
      unfollow: mockedUnfollow,
      isMutating: false,
      error: null,
    });

    createPostSpy = vi.fn();
    uploadSpy = vi.fn();

    mockedUseCreatePost.mockReturnValue({
      createPost: createPostSpy,
      isSubmitting: false,
      error: null,
    });

    mockedUseMediaUpload.mockReturnValue({
      upload: uploadSpy,
      isUploading: false,
      error: null,
    });
  });

  it("renders post composer and feed timeline (empty feed until posts are published)", async () => {
    renderFeedPage();

    expect(screen.getByTestId("feed-page")).toBeInTheDocument();
    expect(screen.getByTestId("create-post-card")).toBeInTheDocument();
    expect(screen.getByText("For you (Graph Feed)")).toBeInTheDocument();
    // The feed starts empty — no PostCards rendered yet.
    expect(screen.queryAllByTestId(/^post-card-/)).toHaveLength(0);
  });

  it("publishes a new post to the timeline after a successful createPost call", async () => {
    createPostSpy.mockResolvedValueOnce(buildCreatePostResponse());

    renderFeedPage();

    const textarea = screen.getByPlaceholderText(
      "Share an update with your federated graph...",
    );
    fireEvent.change(textarea, { target: { value: "Testing post publish" } });

    const publishBtn = screen.getByRole("button", { name: /publish/i });
    fireEvent.click(publishBtn);

    await waitFor(() => {
      expect(createPostSpy).toHaveBeenCalledTimes(1);
    });
    expect(createPostSpy).toHaveBeenCalledWith({
      content: "Testing post publish",
      mediaUrl: undefined,
    });

    await waitFor(() => {
      expect(
        screen.getAllByText("Testing post publish").length,
      ).toBeGreaterThanOrEqual(1);
    });
    expect(screen.getByTestId("post-card-post-new")).toBeInTheDocument();
  });

  it("does NOT add a post to the feed when createPost returns null (failure)", async () => {
    createPostSpy.mockResolvedValueOnce(null);

    renderFeedPage();

    const textarea = screen.getByPlaceholderText(
      "Share an update with your federated graph...",
    );
    fireEvent.change(textarea, { target: { value: "This should fail" } });

    const publishBtn = screen.getByRole("button", { name: /publish/i });
    fireEvent.click(publishBtn);

    await waitFor(() => {
      expect(createPostSpy).toHaveBeenCalledTimes(1);
    });

    // Give any stray state updates a chance to fire.
    await new Promise((r) => setTimeout(r, 50));

    expect(screen.queryAllByTestId(/^post-card-/)).toHaveLength(0);
    expect(screen.queryByText("This should fail")).not.toBeInTheDocument();
  });

  it("uploads the attached file first and forwards the fileUrl as mediaUrl", async () => {
    const uploadResponse: MediaUploadResponse = {
      fileUrl: "https://cdn.wyrdly.app/posts/img_abc.jpg",
      storageKey: "posts/img_abc.jpg",
      mimeType: "image/jpeg",
      fileSizeBytes: 2048,
      uploadedAt: "2026-01-15T10:00:00Z",
    };
    uploadSpy.mockResolvedValueOnce(uploadResponse);
    createPostSpy.mockResolvedValueOnce(
      buildCreatePostResponse({
        id: "post-with-media",
        content: "with media",
        mediaUrl: "https://cdn.wyrdly.app/posts/img_abc.jpg",
      }),
    );

    renderFeedPage();

    const file = new File(["binary"], "photo.jpg", { type: "image/jpeg" });
    const fileInput = screen.getByTestId("file-upload-input");
    fireEvent.change(fileInput, { target: { files: [file] } });

    const publishBtn = screen.getByRole("button", { name: /publish/i });
    fireEvent.click(publishBtn);

    await waitFor(() => {
      expect(uploadSpy).toHaveBeenCalledTimes(1);
    });
    expect(uploadSpy).toHaveBeenCalledWith(file);

    await waitFor(() => {
      expect(createPostSpy).toHaveBeenCalledTimes(1);
    });
    expect(createPostSpy).toHaveBeenCalledWith({
      content: "",
      mediaUrl: "https://cdn.wyrdly.app/posts/img_abc.jpg",
    });

    await waitFor(() => {
      expect(
        screen.getByTestId("post-card-post-with-media"),
      ).toBeInTheDocument();
    });
  });

  it("renders suggestions fetched from useGraphSuggestions", async () => {
    renderFeedPage();

    await waitFor(() => {
      expect(
        screen.getByTestId("user-list-row-user-alice"),
      ).toBeInTheDocument();
      expect(
        screen.getByTestId("user-list-row-user-marcus"),
      ).toBeInTheDocument();
    });

    expect(
      screen.getByText("Followed by Jon and 2 others"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Followed by Priya and 5 others"),
    ).toBeInTheDocument();
  });

  it("calls usersApi.follow when clicking Follow on a non-following suggestion", async () => {
    mockedFollow.mockResolvedValueOnce({
      message: "Followed",
      targetUserId: "user-alice",
      following: true,
    });
    const refetch = vi.fn();
    mockedUseGraphSuggestions.mockReturnValue({
      suggestions: successResponse.data,
      isLoading: false,
      error: null,
      refetch,
    });

    renderFeedPage();

    await waitFor(() => {
      expect(
        screen.getByTestId("follow-toggle-user-alice"),
      ).toBeInTheDocument();
    });

    await act(async () => {
      fireEvent.click(screen.getByTestId("follow-toggle-user-alice"));
    });

    expect(mockedFollow).toHaveBeenCalledTimes(1);
    expect(mockedFollow).toHaveBeenCalledWith("user-alice");
    expect(mockedUnfollow).not.toHaveBeenCalled();
    expect(refetch).toHaveBeenCalled();
  });

  it("calls usersApi.unfollow when clicking Following on a following suggestion", async () => {
    mockedUnfollow.mockResolvedValueOnce({
      message: "Unfollowed",
      targetUserId: "user-marcus",
      following: false,
    });
    const refetch = vi.fn();
    mockedUseGraphSuggestions.mockReturnValue({
      suggestions: successResponse.data,
      isLoading: false,
      error: null,
      refetch,
    });

    renderFeedPage();

    await waitFor(() => {
      expect(
        screen.getByTestId("follow-toggle-user-marcus"),
      ).toBeInTheDocument();
    });

    await act(async () => {
      fireEvent.click(screen.getByTestId("follow-toggle-user-marcus"));
    });

    expect(mockedUnfollow).toHaveBeenCalledTimes(1);
    expect(mockedUnfollow).toHaveBeenCalledWith("user-marcus");
    expect(mockedFollow).not.toHaveBeenCalled();
    expect(refetch).toHaveBeenCalled();
  });

  it("logs but does not refetch when the follow toggle throws", async () => {
    const consoleErrorSpy = vi
      .spyOn(console, "error")
      .mockImplementation(() => {});
    const failure = new Error("network down");
    mockedFollow.mockRejectedValueOnce(failure);
    const refetch = vi.fn();
    mockedUseGraphSuggestions.mockReturnValue({
      suggestions: successResponse.data,
      isLoading: false,
      error: null,
      refetch,
    });

    renderFeedPage();

    await waitFor(() => {
      expect(
        screen.getByTestId("follow-toggle-user-alice"),
      ).toBeInTheDocument();
    });

    await act(async () => {
      fireEvent.click(screen.getByTestId("follow-toggle-user-alice"));
    });

    expect(consoleErrorSpy).toHaveBeenCalledWith(
      "Follow toggle failed",
      failure,
    );
    expect(refetch).not.toHaveBeenCalled();

    consoleErrorSpy.mockRestore();
  });

  it("flips the Follow button to Following immediately on click (optimistic)", async () => {
    mockedFollow.mockResolvedValueOnce({
      message: "Followed",
      targetUserId: "user-alice",
      following: true,
    });
    const refetch = vi.fn();
    mockedUseGraphSuggestions.mockReturnValue({
      suggestions: successResponse.data,
      isLoading: false,
      error: null,
      refetch,
    });

    renderFeedPage();

    await waitFor(() => {
      expect(
        screen.getByTestId("follow-toggle-user-alice"),
      ).toBeInTheDocument();
    });

    const followBtn = screen.getByTestId("follow-toggle-user-alice");
    expect(followBtn).toHaveTextContent("Follow");

    await act(async () => {
      fireEvent.click(followBtn);
    });

    // Immediately after click, before the awaited follow() resolves the next
    // tick, the button must already read "Following".
    expect(screen.getByTestId("follow-toggle-user-alice")).toHaveTextContent(
      "Following",
    );
    expect(mockedFollow).toHaveBeenCalledTimes(1);
  });

  it("rolls back the optimistic Following state when the follow API rejects", async () => {
    const consoleErrorSpy = vi
      .spyOn(console, "error")
      .mockImplementation(() => {});
    mockedFollow.mockRejectedValueOnce(new Error("boom"));
    mockedUseGraphSuggestions.mockReturnValue({
      suggestions: successResponse.data,
      isLoading: false,
      error: null,
      refetch: vi.fn(),
    });

    renderFeedPage();

    await waitFor(() => {
      expect(
        screen.getByTestId("follow-toggle-user-alice"),
      ).toBeInTheDocument();
    });

    await act(async () => {
      fireEvent.click(screen.getByTestId("follow-toggle-user-alice"));
    });

    // The catch path must restore the Follow label.
    await waitFor(() => {
      expect(screen.getByTestId("follow-toggle-user-alice")).toHaveTextContent(
        "Follow",
      );
    });
    expect(consoleErrorSpy).toHaveBeenCalledWith(
      "Follow toggle failed",
      expect.any(Error),
    );
    consoleErrorSpy.mockRestore();
  });

  it("renders post skeletons while feed is loading", async () => {
    mockedGetFeed.mockReturnValue(new Promise(() => {}));

    renderFeedPage();

    expect(screen.getByTestId("feed-posts-loading")).toBeInTheDocument();
    expect(screen.getAllByTestId("post-skeleton")).toHaveLength(3);
  });

  it("renders suggestion skeletons while graph suggestions are loading", async () => {
    mockedUseGraphSuggestions.mockReturnValue({
      suggestions: [],
      isLoading: true,
      error: null,
      refetch: vi.fn(),
    });

    renderFeedPage();

    expect(screen.getByTestId("graph-suggestions-loading")).toBeInTheDocument();
    expect(screen.getAllByTestId("user-list-row-skeleton")).toHaveLength(3);
  });

  it("does not render mock relay health widget or telemetry", async () => {
    renderFeedPage();

    await waitFor(() => {
      expect(screen.getByTestId("feed-page")).toBeInTheDocument();
    });

    expect(screen.queryByTestId("relay-health-widget")).not.toBeInTheDocument();
    expect(screen.queryByText(/Relay health/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/federating with/i)).not.toBeInTheDocument();
  });
});
