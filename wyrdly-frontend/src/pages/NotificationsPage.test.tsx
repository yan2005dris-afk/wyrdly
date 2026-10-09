import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { BrowserRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { NotificationsPage } from "./NotificationsPage";
import { useNotifications } from "../features/notifications";
import { useGraphSuggestions } from "../features/social";
import { AuthProvider } from "../context/AuthContext";
import { postsApi } from "../api/posts";
import type { SocialNotification } from "../features/notifications";

vi.mock("../api/posts", () => ({
  postsApi: {
    getById: vi.fn(),
    react: vi.fn(),
  },
}));

const mockNavigate = vi.fn();
vi.mock("react-router-dom", async () => {
  const actual =
    await vi.importActual<typeof import("react-router-dom")>(
      "react-router-dom",
    );
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

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
    targetResourceId: "pst_123",
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
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  return render(
    <AuthProvider>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <NotificationsPage />
        </BrowserRouter>
      </QueryClientProvider>
    </AuthProvider>,
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

  it("calls markRead when an individual unread notification without targetResourceId is clicked", () => {
    vi.mocked(useNotifications).mockReturnValue({
      notifications: [
        {
          ...mockNotifications[1],
          isRead: false,
        },
      ],
      unreadCount: 1,
      isLoading: false,
      error: null,
      refetch: mockRefetch,
      markRead: mockMarkRead,
      markAllRead: mockMarkAllRead,
    });

    renderPage();

    const notifItem = screen.getByTestId("notification-item-notif-2");
    fireEvent.click(notifItem);

    expect(mockMarkRead).toHaveBeenCalledWith("notif-2");
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

  it("opens PostDetailModal and marks notification as read when clicking a notification with targetResourceId", async () => {
    vi.mocked(postsApi.getById).mockResolvedValueOnce({
      id: "pst_123",
      content: "Post content from notification",
      mediaUrl: null,
      createdAt: "2026-10-08T12:00:00Z",
      author: {
        id: "usr_alice",
        username: "alice",
        fullName: "Alice Chen",
        avatarUrl: null,
      },
      reactionCounts: { likeCount: 2, loveCount: 0, celebrateCount: 0 },
      userReaction: null,
      commentsCount: 1,
    });

    renderPage();

    const notifItem = screen.getByTestId("notification-item-notif-1");
    fireEvent.click(notifItem);

    expect(mockMarkRead).toHaveBeenCalledWith("notif-1");

    await waitFor(() => {
      expect(screen.getByTestId("post-detail-modal")).toBeInTheDocument();
      expect(
        screen.getByText("Post content from notification"),
      ).toBeInTheDocument();
    });

    // Close the modal
    const closeBtn = screen.getByTestId("post-detail-modal-close");
    fireEvent.click(closeBtn);

    await waitFor(() => {
      expect(screen.queryByTestId("post-detail-modal")).not.toBeInTheDocument();
    });
  });

  it("opens PostDetailModal when pressing Enter key on notification item", async () => {
    vi.mocked(postsApi.getById).mockResolvedValueOnce({
      id: "pst_123",
      content: "Keyboard activated post",
      mediaUrl: null,
      createdAt: "2026-10-08T12:00:00Z",
      author: {
        id: "usr_alice",
        username: "alice",
        fullName: "Alice Chen",
        avatarUrl: null,
      },
      reactionCounts: { likeCount: 0, loveCount: 0, celebrateCount: 0 },
      userReaction: null,
      commentsCount: 0,
    });

    renderPage();

    const notifItem = screen.getByTestId("notification-item-notif-1");
    fireEvent.keyDown(notifItem, { key: "Enter" });

    expect(mockMarkRead).toHaveBeenCalledWith("notif-1");

    await waitFor(() => {
      expect(screen.getByTestId("post-detail-modal")).toBeInTheDocument();
      expect(screen.getByText("Keyboard activated post")).toBeInTheDocument();
    });
  });

  it("opens PostDetailModal on notification double click", async () => {
    vi.mocked(postsApi.getById).mockResolvedValueOnce({
      id: "pst_123",
      content: "Double click post",
      mediaUrl: null,
      createdAt: "2026-10-08T12:00:00Z",
      author: {
        id: "usr_alice",
        username: "alice",
        fullName: "Alice Chen",
        avatarUrl: null,
      },
      reactionCounts: { likeCount: 0, loveCount: 0, celebrateCount: 0 },
      userReaction: null,
      commentsCount: 0,
    });

    renderPage();

    const notifItem = screen.getByTestId("notification-item-notif-1");
    fireEvent.doubleClick(notifItem);

    expect(mockMarkRead).toHaveBeenCalledWith("notif-1");

    await waitFor(() => {
      expect(screen.getByTestId("post-detail-modal")).toBeInTheDocument();
      expect(screen.getByText("Double click post")).toBeInTheDocument();
    });
  });

  it("navigates to /chat when interacting with a CHAT_MESSAGE notification and does not open PostDetailModal", () => {
    const chatNotification: SocialNotification = {
      id: "notif-chat-1",
      type: "CHAT_MESSAGE",
      actor: {
        id: "usr_bob",
        username: "bob",
        fullName: "Bob Smith",
        avatarUrl: null,
        isVerified: false,
        instanceUrl: "wyrdly.social",
        stats: { followersCount: 0, followingCount: 0, postsCount: 0 },
      },
      message: "sent you a message",
      targetResourceId: "msg_123",
      createdAt: "5m ago",
      isRead: false,
    };

    vi.mocked(useNotifications).mockReturnValue({
      notifications: [chatNotification],
      unreadCount: 1,
      isLoading: false,
      error: null,
      refetch: mockRefetch,
      markRead: mockMarkRead,
      markAllRead: mockMarkAllRead,
    });

    renderPage();

    const notifItem = screen.getByTestId("notification-item-notif-chat-1");
    fireEvent.click(notifItem);

    expect(mockMarkRead).toHaveBeenCalledWith("notif-chat-1");
    expect(mockNavigate).toHaveBeenCalledWith(
      "/chat?userId=usr_bob&username=bob",
    );
    expect(screen.queryByTestId("post-detail-modal")).not.toBeInTheDocument();
  });
});
