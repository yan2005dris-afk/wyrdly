import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ChatHeader } from "./ChatHeader";
import type { UserProfileSummary } from "../../../../types/domain";

const MOCK_PARTICIPANT: UserProfileSummary = {
  id: "user-alice",
  username: "alice",
  fullName: "Alice Chen",
  avatarUrl: "https://example.com/alice.jpg",
  isVerified: true,
  instanceUrl: "wyrdly.app",
  stats: { followersCount: 10, followingCount: 5, postsCount: 20 },
};

describe("ChatHeader Component", () => {
  it("renders participant name, status and verified badge", () => {
    render(
      <ChatHeader
        participant={MOCK_PARTICIPANT}
        isOnline={true}
        statusText="Online • typing via relay..."
      />,
    );

    expect(screen.getByText("Alice Chen")).toBeInTheDocument();
    expect(
      screen.getByText("Online • typing via relay..."),
    ).toBeInTheDocument();
    expect(screen.getByTestId("chat-verified-badge")).toBeInTheDocument();
  });

  it("handles call and video button clicks", () => {
    const handleCall = vi.fn();
    const handleVideo = vi.fn();
    render(
      <ChatHeader
        participant={MOCK_PARTICIPANT}
        onCallClick={handleCall}
        onVideoClick={handleVideo}
      />,
    );

    fireEvent.click(screen.getByTestId("chat-call-btn"));
    expect(handleCall).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByTestId("chat-video-btn"));
    expect(handleVideo).toHaveBeenCalledTimes(1);
  });
});
