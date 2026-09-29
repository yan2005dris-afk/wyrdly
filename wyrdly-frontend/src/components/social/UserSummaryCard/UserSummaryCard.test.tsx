import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { BrowserRouter } from "react-router-dom";
import { UserSummaryCard } from "./UserSummaryCard";
import type { UserProfileSummary } from "../../../types/domain";

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
  it("renders user information and formatted counts", () => {
    render(
      <BrowserRouter>
        <UserSummaryCard user={MOCK_USER} />
      </BrowserRouter>,
    );

    expect(screen.getByText("Maya Krishnan")).toBeInTheDocument();
    expect(screen.getByText(/@maya • wyrdly.app/)).toBeInTheDocument();
    expect(screen.getByTestId("followers-stat")).toHaveTextContent("12.4k");
    expect(screen.getByTestId("following-stat")).toHaveTextContent("890");
    expect(screen.getByTestId("posts-stat")).toHaveTextContent("3.1k");
    expect(screen.getByTestId("verified-badge")).toBeInTheDocument();
  });
});
