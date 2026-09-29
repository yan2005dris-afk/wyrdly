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

vi.mock("../hooks/useGraphSuggestions", () => ({
  useGraphSuggestions: vi.fn(),
}));

vi.mock("../hooks/useFollow", () => ({
  useFollow: vi.fn(),
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

import { useGraphSuggestions } from "../hooks/useGraphSuggestions";
import { useFollow } from "../hooks/useFollow";

const mockedUseGraphSuggestions = vi.mocked(useGraphSuggestions);
const mockedUseFollow = vi.mocked(useFollow);
const mockedFollow = vi.mocked(usersApi.follow);
const mockedUnfollow = vi.mocked(usersApi.unfollow);

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
  beforeEach(() => {
    mockedUseGraphSuggestions.mockReset();
    mockedUseFollow.mockReset();
    mockedFollow.mockReset();
    mockedUnfollow.mockReset();

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
  });

  it("renders post composer and feed timeline", async () => {
    renderFeedPage();

    expect(screen.getByTestId("feed-page")).toBeInTheDocument();
    expect(screen.getByTestId("create-post-card")).toBeInTheDocument();
    expect(screen.getByText("For you (Graph Feed)")).toBeInTheDocument();
    expect(screen.getByText("Jonas Weber")).toBeInTheDocument();
    // Alice appears in both suggestions and as the author of post-2.
    await waitFor(() => {
      expect(screen.getAllByText("Alice Chen").length).toBeGreaterThanOrEqual(
        1,
      );
    });
  });

  it("publishes a new post to the timeline", async () => {
    renderFeedPage();

    const textarea = screen.getByPlaceholderText(
      "Share an update with your federated graph...",
    );
    fireEvent.change(textarea, { target: { value: "Testing post publish" } });

    const publishBtn = screen.getByRole("button", { name: /publish/i });
    fireEvent.click(publishBtn);

    await waitFor(() => {
      expect(
        screen.getAllByText("Testing post publish").length,
      ).toBeGreaterThanOrEqual(1);
    });
  });

  it("renders suggestions fetched from useGraphSuggestions", async () => {
    renderFeedPage();

    await waitFor(() => {
      expect(
        screen.getByTestId("suggestion-item-user-alice"),
      ).toBeInTheDocument();
      expect(
        screen.getByTestId("suggestion-item-user-marcus"),
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
      expect(screen.getByTestId("follow-btn-user-alice")).toBeInTheDocument();
    });

    await act(async () => {
      fireEvent.click(screen.getByTestId("follow-btn-user-alice"));
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
      expect(screen.getByTestId("follow-btn-user-marcus")).toBeInTheDocument();
    });

    await act(async () => {
      fireEvent.click(screen.getByTestId("follow-btn-user-marcus"));
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
      expect(screen.getByTestId("follow-btn-user-alice")).toBeInTheDocument();
    });

    await act(async () => {
      fireEvent.click(screen.getByTestId("follow-btn-user-alice"));
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
      expect(screen.getByTestId("follow-btn-user-alice")).toBeInTheDocument();
    });

    const followBtn = screen.getByTestId("follow-btn-user-alice");
    expect(followBtn).toHaveTextContent("Follow");

    await act(async () => {
      fireEvent.click(followBtn);
    });

    // Immediately after click, before the awaited follow() resolves the next
    // tick, the button must already read "Following".
    expect(screen.getByTestId("follow-btn-user-alice")).toHaveTextContent(
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
      expect(screen.getByTestId("follow-btn-user-alice")).toBeInTheDocument();
    });

    await act(async () => {
      fireEvent.click(screen.getByTestId("follow-btn-user-alice"));
    });

    // The catch path must restore the Follow label.
    await waitFor(() => {
      expect(screen.getByTestId("follow-btn-user-alice")).toHaveTextContent(
        "Follow",
      );
    });
    expect(consoleErrorSpy).toHaveBeenCalledWith(
      "Follow toggle failed",
      expect.any(Error),
    );
    consoleErrorSpy.mockRestore();
  });
});
