import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { BrowserRouter } from "react-router-dom";
import { UserSummaryCard } from "./UserSummaryCard";
import type { UserProfileSummary } from "../../../../types/domain";

const MOCK_USER: UserProfileSummary = {
  id: "user-1",
  username: "maya",
  fullName: "Maya Krishnan",
  avatarUrl: "https://example.com/maya.jpg",
  isVerified: true,
  instanceUrl: "wyrdly.app",
  stats: {
    followersCount: 12400,
    followingCount: 890,
    postsCount: 3100,
  },
};

describe("UserSummaryCard Component", () => {
  it("renders clean user identity without stats by default in sidebar context", () => {
    render(
      <BrowserRouter>
        <UserSummaryCard user={MOCK_USER} />
      </BrowserRouter>,
    );

    expect(screen.getByText("Maya Krishnan")).toBeInTheDocument();
    expect(screen.getByText(/@maya • wyrdly.app/)).toBeInTheDocument();
    expect(screen.getByTestId("verified-badge")).toBeInTheDocument();
    expect(screen.getByTestId("view-profile-link")).toBeInTheDocument();

    // Verify stats are retired/absent by default
    expect(screen.queryByTestId("user-summary-stats")).not.toBeInTheDocument();
    expect(screen.queryByTestId("followers-stat")).not.toBeInTheDocument();
    expect(screen.queryByTestId("following-stat")).not.toBeInTheDocument();
    expect(screen.queryByTestId("posts-stat")).not.toBeInTheDocument();
  });

  it("renders formatted counts when showStats is explicitly enabled", () => {
    render(
      <BrowserRouter>
        <UserSummaryCard user={MOCK_USER} showStats={true} />
      </BrowserRouter>,
    );

    expect(screen.getByTestId("user-summary-stats")).toBeInTheDocument();
    expect(screen.getByTestId("followers-stat")).toHaveTextContent("12.4k");
    expect(screen.getByTestId("following-stat")).toHaveTextContent("890");
    expect(screen.getByTestId("posts-stat")).toHaveTextContent("3.1k");
  });

  it("fires onProfileClick callback when user triggers navigation", () => {
    const handleProfileClick = vi.fn();
    render(
      <BrowserRouter>
        <UserSummaryCard user={MOCK_USER} onProfileClick={handleProfileClick} />
      </BrowserRouter>,
    );

    const profileLink = screen.getByText("Maya Krishnan");
    fireEvent.click(profileLink);
    expect(handleProfileClick).toHaveBeenCalledWith("maya");

    const viewBtn = screen.getByTestId("view-profile-link");
    fireEvent.click(viewBtn);
    expect(handleProfileClick).toHaveBeenCalledWith("maya");
  });
});
