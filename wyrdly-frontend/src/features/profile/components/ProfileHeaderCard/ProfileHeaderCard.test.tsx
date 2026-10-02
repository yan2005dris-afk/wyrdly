import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ProfileHeaderCard } from "./ProfileHeaderCard";
import type { UserProfileSummary } from "../../../types/domain";

const MOCK_USER: UserProfileSummary = {
  id: "user-maya",
  username: "maya",
  fullName: "Maya Krishnan",
  avatarUrl: "https://example.com/maya.jpg",
  isVerified: true,
  instanceUrl: "wyrdly.app",
  bio: "Distributed systems engineer. Building open relays & photography enthusiast.",
  location: "Bengaluru, IN",
  website: "maya.build",
  joinedDate: "March 2023",
  homeRelay: "ap-south-1",
  stats: {
    followersCount: 12400,
    followingCount: 890,
    postsCount: 3100,
  },
};

describe("ProfileHeaderCard Component", () => {
  it("renders user profile info, bio, metadata and RustFS cover badge", () => {
    render(
      <ProfileHeaderCard
        user={MOCK_USER}
        coverUrl="https://example.com/cover.jpg"
        activeTab="posts"
        onTabChange={vi.fn()}
      />,
    );

    expect(screen.getByText("Maya Krishnan")).toBeInTheDocument();
    expect(
      screen.getByText(/Distributed systems engineer/),
    ).toBeInTheDocument();
    expect(screen.getByText("Bengaluru, IN")).toBeInTheDocument();
    expect(screen.getByText("maya.build")).toBeInTheDocument();
    expect(screen.getByText("Cover stored on RustFS")).toBeInTheDocument();
    expect(screen.getByTestId("profile-verified-badge")).toBeInTheDocument();
  });

  it("renders Action buttons and handles subscribe toggle", () => {
    const handleSubscribe = vi.fn();
    render(
      <ProfileHeaderCard
        user={MOCK_USER}
        activeTab="posts"
        onTabChange={vi.fn()}
        onSubscribeToggle={handleSubscribe}
      />,
    );

    const subscribeBtn = screen.getByTestId("profile-subscribe-btn");
    expect(subscribeBtn).toHaveTextContent("Subscribe");
    fireEvent.click(subscribeBtn);
    expect(handleSubscribe).toHaveBeenCalledTimes(1);
  });

  it("handles tab switching", () => {
    const handleTabChange = vi.fn();
    render(
      <ProfileHeaderCard
        user={MOCK_USER}
        activeTab="posts"
        onTabChange={handleTabChange}
      />,
    );

    const followersTab = screen.getByTestId("tab-followers");
    fireEvent.click(followersTab);
    expect(handleTabChange).toHaveBeenCalledWith("followers");
  });

  it("renders social graph stats counts (Following, Followers, Posts) as a primary block", () => {
    render(
      <ProfileHeaderCard
        user={MOCK_USER}
        activeTab="posts"
        onTabChange={vi.fn()}
      />,
    );

    expect(screen.getByTestId("profile-stats-row")).toBeInTheDocument();
    expect(screen.getByTestId("profile-stat-following")).toHaveTextContent(
      "890 Following",
    );
    expect(screen.getByTestId("profile-stat-followers")).toHaveTextContent(
      "12.4k Followers",
    );
    expect(screen.getByTestId("profile-stat-posts")).toHaveTextContent(
      "3.1k Posts",
    );
  });
});
