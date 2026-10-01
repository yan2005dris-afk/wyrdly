import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { NotificationPopover } from "./NotificationPopover";
import type { SocialNotification } from "../../types";

const MOCK_NOTIFICATIONS: SocialNotification[] = [
  {
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
    targetSnippet: "Relay v2 benchmarks",
    createdAt: "2m ago",
    isRead: false,
  },
];

describe("NotificationPopover Component", () => {
  it("renders header, notifications list and footer", () => {
    render(
      <NotificationPopover
        notifications={MOCK_NOTIFICATIONS}
        onMarkAllAsRead={vi.fn()}
        onViewAllActivity={vi.fn()}
      />,
    );

    expect(screen.getByText("Notifications")).toBeInTheDocument();
    expect(screen.getByText("Alice Chen")).toBeInTheDocument();
    expect(screen.getByText("Mark all read")).toBeInTheDocument();
    expect(screen.getByText("View all activity")).toBeInTheDocument();
  });

  it("handles mark all as read click", () => {
    const handleMarkRead = vi.fn();
    render(
      <NotificationPopover
        notifications={MOCK_NOTIFICATIONS}
        onMarkAllAsRead={handleMarkRead}
      />,
    );

    fireEvent.click(screen.getByTestId("mark-all-read-btn"));
    expect(handleMarkRead).toHaveBeenCalledTimes(1);
  });
});
