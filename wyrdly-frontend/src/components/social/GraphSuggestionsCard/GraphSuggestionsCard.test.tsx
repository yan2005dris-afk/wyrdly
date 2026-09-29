import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { BrowserRouter } from "react-router-dom";
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

describe("GraphSuggestionsCard Component", () => {
  it("renders suggestions list and mutual connection text", () => {
    render(
      <BrowserRouter>
        <GraphSuggestionsCard
          suggestions={MOCK_SUGGESTIONS}
          onFollowToggle={vi.fn()}
        />
      </BrowserRouter>,
    );

    expect(screen.getByText("Alice Chen")).toBeInTheDocument();
    expect(
      screen.getByText("Followed by Jon and 2 others"),
    ).toBeInTheDocument();
    expect(screen.getByText("Marcus Cole")).toBeInTheDocument();
  });

  it("handles follow button toggle", () => {
    const handleToggle = vi.fn();
    render(
      <BrowserRouter>
        <GraphSuggestionsCard
          suggestions={MOCK_SUGGESTIONS}
          onFollowToggle={handleToggle}
        />
      </BrowserRouter>,
    );

    const aliceFollowBtn = screen.getByTestId("follow-btn-user-alice");
    expect(aliceFollowBtn).toHaveTextContent("Follow");
    fireEvent.click(aliceFollowBtn);

    expect(handleToggle).toHaveBeenCalledWith("user-alice");

    const marcusFollowBtn = screen.getByTestId("follow-btn-user-marcus");
    expect(marcusFollowBtn).toHaveTextContent("Following");
  });
});
