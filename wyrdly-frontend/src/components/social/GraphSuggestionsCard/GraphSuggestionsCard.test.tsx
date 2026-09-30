import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { BrowserRouter } from "react-router-dom";
import { AuthProvider } from "../../../context/AuthContext";
import * as usersApi from "../../../api/users";
import { GraphSuggestionsCard } from "./GraphSuggestionsCard";
import type { GraphSuggestionUser } from "../../../types/domain";

const MOCK_SUGGESTIONS: GraphSuggestionUser[] = [
  {
    id: "user-alice",
    username: "alice",
    fullName: "Alice Chen",
    avatarUrl: "https://example.com/alice.jpg",
    mutualConnectionSnippet: "Followed by Jon and 2 others",
    isFollowing: false,
  },
  {
    id: "user-marcus",
    username: "marcus",
    fullName: "Marcus Cole",
    avatarUrl: "https://example.com/marcus.jpg",
    mutualConnectionSnippet: "Followed by Priya and 5 others",
    isFollowing: true,
  },
];

function renderCard(
  props: Partial<Parameters<typeof GraphSuggestionsCard>[0]> = {},
) {
  return render(
    <AuthProvider>
      <BrowserRouter>
        <GraphSuggestionsCard suggestions={MOCK_SUGGESTIONS} {...props} />
      </BrowserRouter>
    </AuthProvider>,
  );
}

describe("GraphSuggestionsCard Component", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.setItem("wyrdly_token", "fake-jwt");
    vi.spyOn(usersApi.usersApi, "follow").mockResolvedValue({
      message: "ok",
      targetUserId: "user-alice",
      following: true,
    });
  });

  it("renders suggestions list and mutual connection text", () => {
    renderCard();

    expect(screen.getByText("Alice Chen")).toBeInTheDocument();
    expect(
      screen.getByText("Followed by Jon and 2 others"),
    ).toBeInTheDocument();
    expect(screen.getByText("Marcus Cole")).toBeInTheDocument();
  });

  it("renders Follow / Following labels per the server-side isFollowing flag", () => {
    renderCard();

    expect(screen.getByTestId("follow-toggle-user-alice")).toHaveTextContent(
      "Follow",
    );
    expect(screen.getByTestId("follow-toggle-user-marcus")).toHaveTextContent(
      "Following",
    );
  });

  it("calls onSeeAllClick when clicking see all recommendations button", () => {
    const handleSeeAll = vi.fn();
    renderCard({ onSeeAllClick: handleSeeAll });

    const btn = screen.getByTestId("see-all-suggestions-btn");
    expect(btn).toBeInTheDocument();
    fireEvent.click(btn);
    expect(handleSeeAll).toHaveBeenCalledTimes(1);
  });

  it("forwards onAfterToggle to child rows", async () => {
    const handleAfterToggle = vi.fn();
    renderCard({ onAfterToggle: handleAfterToggle });

    const btn = screen.getByTestId("follow-toggle-user-alice");
    fireEvent.click(btn);

    await waitFor(() => {
      expect(handleAfterToggle).toHaveBeenCalledTimes(1);
    });
    expect(handleAfterToggle).toHaveBeenCalledWith("user-alice");
  });
});
