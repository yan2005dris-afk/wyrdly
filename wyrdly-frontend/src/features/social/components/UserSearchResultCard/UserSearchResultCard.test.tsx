import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { BrowserRouter } from "react-router-dom";
import { UserSearchResultCard } from "./UserSearchResultCard";
import type { UserSearchResult } from "../../../../types/userSearch";

const baseResult: UserSearchResult = {
  id: "usr_alice",
  username: "alice",
  fullName: "Alice Chen",
  avatarUrl: "https://example.com/alice.jpg",
  bio: "Backend dev",
  isFollowing: false,
  mutualConnectionSnippet: null,
};

const renderInRouter = (ui: React.ReactNode) =>
  render(<BrowserRouter>{ui}</BrowserRouter>);

describe("UserSearchResultCard Component", () => {
  it("renders username, fullName and bio", () => {
    renderInRouter(
      <UserSearchResultCard result={baseResult} onFollowToggle={vi.fn()} />,
    );

    expect(screen.getByText("@alice")).toBeInTheDocument();
    expect(screen.getByText("Alice Chen")).toBeInTheDocument();
    expect(screen.getByText("Backend dev")).toBeInTheDocument();
  });

  it("hides bio when null", () => {
    renderInRouter(
      <UserSearchResultCard
        result={{ ...baseResult, bio: null }}
        onFollowToggle={vi.fn()}
      />,
    );

    expect(
      screen.queryByTestId("user-search-bio-usr_alice"),
    ).not.toBeInTheDocument();
  });

  it("shows mutual snippet when present", () => {
    renderInRouter(
      <UserSearchResultCard
        result={{ ...baseResult, mutualConnectionSnippet: "3 amigos en común" }}
        onFollowToggle={vi.fn()}
      />,
    );

    expect(
      screen.getByTestId("user-search-mutual-usr_alice"),
    ).toHaveTextContent("3 amigos en común");
  });

  it("shows Follow when isFollowing is false", () => {
    renderInRouter(
      <UserSearchResultCard result={baseResult} onFollowToggle={vi.fn()} />,
    );

    expect(
      screen.getByTestId("user-search-follow-btn-usr_alice"),
    ).toHaveTextContent("Follow");
  });

  it("shows Following when isFollowing is true", () => {
    renderInRouter(
      <UserSearchResultCard
        result={{ ...baseResult, isFollowing: true }}
        onFollowToggle={vi.fn()}
      />,
    );

    expect(
      screen.getByTestId("user-search-follow-btn-usr_alice"),
    ).toHaveTextContent("Following");
  });

  it("calls onFollowToggle with the userId when follow button clicked", () => {
    const handleToggle = vi.fn();
    renderInRouter(
      <UserSearchResultCard
        result={baseResult}
        onFollowToggle={handleToggle}
      />,
    );

    fireEvent.click(screen.getByTestId("user-search-follow-btn-usr_alice"));
    expect(handleToggle).toHaveBeenCalledWith("usr_alice");
  });

  it("does not navigate when follow button is clicked (preventDefault)", () => {
    const handleToggle = vi.fn();
    renderInRouter(
      <UserSearchResultCard
        result={baseResult}
        onFollowToggle={handleToggle}
      />,
    );

    const button = screen.getByTestId("user-search-follow-btn-usr_alice");
    const event = new MouseEvent("click", { bubbles: true, cancelable: true });
    button.dispatchEvent(event);

    expect(event.defaultPrevented).toBe(true);
    expect(handleToggle).toHaveBeenCalled();
  });

  it("links to the user profile", () => {
    renderInRouter(
      <UserSearchResultCard result={baseResult} onFollowToggle={vi.fn()} />,
    );

    const link = screen.getByTestId("user-search-result-usr_alice");
    expect(link).toHaveAttribute("href", "/profile/alice");
  });
});
