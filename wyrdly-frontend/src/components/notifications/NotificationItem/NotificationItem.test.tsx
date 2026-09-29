import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { NotificationItem } from "./NotificationItem";
import type { SocialNotification } from "../../../types/notifications";

const MOCK_NOTIFICATION: SocialNotification = {
  id: "notif-1",
  type: "POST_LIKE",
  actor: {
    id: "user-alice",
    username: "alice",
    fullName: "Alice Chen",
    avatarUrl: "https://example.com/alice.jpg",
    isVerified: true,
    instanceUrl: "wyrdly.app",
    stats: { followersCount: 10, followingCount: 5, postsCount: 20 },
  },
  message: "loved your post",
  targetSnippet: "Relay v2 benchmarks are live",
  createdAt: "2m ago",
  isRead: false,
};

describe("NotificationItem Component", () => {
  it("renders notification actor, message, snippet and type badge", () => {
    render(<NotificationItem notification={MOCK_NOTIFICATION} />);

    expect(screen.getByText("Alice Chen")).toBeInTheDocument();
    expect(screen.getByText("loved your post")).toBeInTheDocument();
    expect(
      screen.getByText('"Relay v2 benchmarks are live"'),
    ).toBeInTheDocument();
    expect(screen.getByText("2m ago")).toBeInTheDocument();
    expect(screen.getByTestId("type-badge-POST_LIKE")).toBeInTheDocument();
  });

  it("handles item click and fires callback", () => {
    const handleClick = vi.fn();
    render(
      <NotificationItem
        notification={MOCK_NOTIFICATION}
        onClick={handleClick}
      />,
    );

    const item = screen.getByTestId("notification-item-notif-1");
    fireEvent.click(item);
    expect(handleClick).toHaveBeenCalledWith("notif-1");
  });
});
