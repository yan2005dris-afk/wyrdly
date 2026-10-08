import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { BrowserRouter } from "react-router-dom";
import { NotificationsPage } from "./NotificationsPage";
import { useNotifications } from "../features/notifications";
import { useGraphSuggestions } from "../features/social";
import type { SocialNotification } from "../features/notifications";

vi.mock("../features/notifications", async () => {
  const actual = await vi.importActual<
    typeof import("../features/notifications")
  >("../features/notifications");
  return {
    ...actual,
    useNotifications: vi.fn(),
  };
});

vi.mock("../features/social", async () => {
  const actual =
    await vi.importActual<typeof import("../features/social")>(
      "../features/social",
    );
  return {
    ...actual,
    useGraphSuggestions: vi.fn(),
  };
});

const mockNotifications: SocialNotification[] = [
  {
    id: "notif-1",
    type: "POST_LIKE",
    actor: {
      id: "usr-alice",
      username: "alice",
      fullName: "Alice Chen",
      avatarUrl: "https://example.com/alice.jpg",
      instanceUrl: "wyrdly.social",
      isVerified: true,
      stats: { followersCount: 10, followingCount: 5, postsCount: 2 },
    },
    message: "liked your post",
    targetSnippet: "Federated graph traversal",
    createdAt: "5m ago",
    isRead: false,
  },
  {
    id: "notif-2",
    type: "GRAPH_FOLLOW",
    actor: {
      id: "usr-bob",
      username: "bob",
      fullName: "Bob Smith",
      avatarUrl: "https://example.com/bob.jpg",
      instanceUrl: "wyrdly.social",
      isVerified: false,
      stats: { followersCount: 20, followingCount: 15, postsCount: 10 },
    },
    message: "started following you",
    createdAt: "1h ago",
    isRead: true,
  },
];

const mockMarkRead = vi.fn();
const mockMarkAllRead = vi.fn();
const mockRefetch = vi.fn();
const mockRefetchSuggestions = vi.fn();

const renderPage = () => {
  return render(
    <BrowserRouter>
      <NotificationsPage />
    </BrowserRouter>,
  );
};

describe("NotificationsPage Component", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useGraphSuggestions).mockReturnValue({
      suggestions: [],
      isLoading: false,
      error: null,
      refetch: mockRefetchSuggestions,
    });
    vi.mocked(useNotifications).mockReturnValue({
      notifications: mockNotifications,
      unreadCount: 1,
      isLoading: false,
      error: null,
      refetch: mockRefetch,
      markRead: mockMarkRead,
      markAllRead: mockMarkAllRead,
    });
  });

  it("renders page header, unread badge, and notification list", () => {
    renderPage();

    expect(screen.getByTestId("notifications-page")).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: /notifications/i }),
    ).toBeInTheDocument();
    expect(screen.getByTestId("unread-count-badge")).toHaveTextContent(
      "1 unread",
    );
    expect(screen.getByTestId("notification-item-notif-1")).toBeInTheDocument();
    expect(screen.getByTestId("notification-item-notif-2")).toBeInTheDocument();
    expect(screen.getByText("Alice Chen")).toBeInTheDocument();
    expect(screen.getByText("liked your post")).toBeInTheDocument();
  });

  it("filters notifications by unread tab", async () => {
    renderPage();

    expect(screen.getByTestId("notification-item-notif-1")).toBeInTheDocument();
    expect(screen.getByTestId("notification-item-notif-2")).toBeInTheDocument();

    const unreadTab = screen.getByTestId("tab-unread");
    fireEvent.click(unreadTab);

    await waitFor(() => {
      expect(
        screen.getByTestId("notification-item-notif-1"),
      ).toBeInTheDocument();
      expect(
        screen.queryByTestId("notification-item-notif-2"),
      ).not.toBeInTheDocument();
    });
  });

  it("calls markAllRead when Mark all read button is clicked", () => {
    renderPage();

    const markAllBtn = screen.getByTestId("mark-all-read-btn");
    expect(markAllBtn).toBeEnabled();
    fireEvent.click(markAllBtn);

    expect(mockMarkAllRead).toHaveBeenCalledTimes(1);
  });

  it("calls markRead when an individual notification is clicked", () => {
    renderPage();

    const notifItem = screen.getByTestId("notification-item-notif-1");
    fireEvent.click(notifItem);

    expect(mockMarkRead).toHaveBeenCalledWith("notif-1");
  });

  it("renders loading skeletons when isLoading is true", () => {
    vi.mocked(useNotifications).mockReturnValue({
      notifications: [],
      unreadCount: 0,
      isLoading: true,
      error: null,
      refetch: mockRefetch,
      markRead: mockMarkRead,
      markAllRead: mockMarkAllRead,
    });

    renderPage();

    expect(screen.getByTestId("notifications-loading")).toBeInTheDocument();
    expect(screen.getAllByTestId("notification-skeleton")).toHaveLength(4);
    expect(screen.getByTestId("mark-all-read-btn")).toBeDisabled();
  });

  it("renders empty state when there are no notifications", () => {
    vi.mocked(useNotifications).mockReturnValue({
      notifications: [],
      unreadCount: 0,
      isLoading: false,
      error: null,
      refetch: mockRefetch,
      markRead: mockMarkRead,
      markAllRead: mockMarkAllRead,
    });

    renderPage();

    expect(screen.getByTestId("notifications-empty")).toBeInTheDocument();
    expect(screen.getByText("No notifications yet")).toBeInTheDocument();
  });

  it("renders empty state when unread tab has no unread notifications", async () => {
    vi.mocked(useNotifications).mockReturnValue({
      notifications: [
        {
          ...mockNotifications[1],
          isRead: true,
        },
      ],
      unreadCount: 0,
      isLoading: false,
      error: null,
      refetch: mockRefetch,
      markRead: mockMarkRead,
      markAllRead: mockMarkAllRead,
    });

    renderPage();

    const unreadTab = screen.getByTestId("tab-unread");
    fireEvent.click(unreadTab);

    await waitFor(() => {
      expect(screen.getByTestId("notifications-empty")).toBeInTheDocument();
      expect(screen.getByText("No unread notifications")).toBeInTheDocument();
    });
  });

  it("renders error banner and retry button when error occurs", () => {
    vi.mocked(useNotifications).mockReturnValue({
      notifications: [],
      unreadCount: 0,
      isLoading: false,
      error: new Error("Network timeout"),
      refetch: mockRefetch,
      markRead: mockMarkRead,
      markAllRead: mockMarkAllRead,
    });

    renderPage();

    expect(screen.getByTestId("notifications-error")).toBeInTheDocument();
    expect(
      screen.getByText(/Failed to load notifications: Network timeout/),
    ).toBeInTheDocument();

    const retryBtn = screen.getByTestId("retry-btn");
    fireEvent.click(retryBtn);
    expect(mockRefetch).toHaveBeenCalledTimes(1);
  });
});
