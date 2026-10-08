import {
  render,
  screen,
  fireEvent,
  waitFor,
  act,
} from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { ChatPage } from "./ChatPage";
import { AuthContext } from "../features/auth";
import { usersApi } from "../api/users";
import { chatApi, useUnreadMessagesStore } from "../features/chat";
import { useChatWebSocket } from "../features/chat/hooks/useChatWebSocket";
import type { User } from "../features/auth";

vi.mock("../features/chat/hooks/useChatWebSocket", () => ({
  useChatWebSocket: vi.fn().mockReturnValue({
    sendMessage: vi.fn().mockReturnValue(true),
    sendTyping: vi.fn(),
    isConnected: true,
  }),
}));

const MOCK_USER: User = {
  id: "user-current",
  username: "testuser",
  email: "test@wyrdly.local",
  fullName: "Test User",
  avatarUrl: "https://example.com/avatar.jpg",
  bio: "Bio",
  instanceUrl: "wyrdly.social",
  isVerified: false,
  roles: ["USER"],
  status: "ACTIVE",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const MOCK_FOLLOWING = [
  {
    id: "user-alice",
    username: "alice",
    fullName: "Alice Chen",
    avatarUrl: "https://example.com/alice.jpg",
  },
  {
    id: "user-jonas",
    username: "jonas",
    fullName: "Jonas Weber",
    avatarUrl: "https://example.com/jonas.jpg",
  },
];

const renderChatPage = ({
  user = MOCK_USER,
  initialEntries = ["/chat"],
}: {
  user?: User | null;
  initialEntries?: string[];
} = {}) => {
  window.HTMLElement.prototype.scrollIntoView = vi.fn();

  return render(
    <AuthContext.Provider
      value={{
        user,
        token: user ? "mock-jwt-token" : null,
        isAuthenticated: !!user,
        isLoading: false,
        error: null,
        login: vi.fn(),
        register: vi.fn(),
        logout: vi.fn(),
        updateUser: vi.fn(),
        clearError: vi.fn(),
      }}
    >
      <MemoryRouter initialEntries={initialEntries}>
        <ChatPage />
      </MemoryRouter>
    </AuthContext.Provider>,
  );
};

describe("ChatPage Component", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it("renders empty state when user has no followed users", async () => {
    vi.spyOn(usersApi, "getUserFollowing").mockResolvedValue([]);

    renderChatPage();

    await waitFor(() => {
      expect(screen.getByTestId("chat-page")).toBeInTheDocument();
      expect(screen.getByTestId("conversation-list")).toBeInTheDocument();
      expect(screen.getByTestId("chat-empty-selection")).toBeInTheDocument();
      expect(screen.getByText("No conversation selected")).toBeInTheDocument();
    });
  });

  it("renders skeleton loading state while conversations are loading", async () => {
    let resolveFollowing: (val: typeof MOCK_FOLLOWING) => void;
    vi.spyOn(usersApi, "getUserFollowing").mockReturnValue(
      new Promise((resolve) => {
        resolveFollowing = resolve;
      }),
    );

    renderChatPage();

    expect(screen.getByTestId("conversation-list-loading")).toBeInTheDocument();
    expect(screen.getAllByTestId("conversation-item-skeleton").length).toBe(4);

    resolveFollowing!(MOCK_FOLLOWING);
    await waitFor(() => {
      expect(screen.getAllByText("Alice Chen").length).toBeGreaterThanOrEqual(
        1,
      );
    });
  });

  it("renders loaded conversations and active chat window", async () => {
    vi.spyOn(usersApi, "getUserFollowing").mockResolvedValue(MOCK_FOLLOWING);
    vi.spyOn(chatApi, "getChatHistory").mockResolvedValue({
      data: [
        {
          id: "m-1",
          senderId: "user-alice",
          recipientId: "user-current",
          content: "Hello from Alice!",
          sentAt: "2026-10-02T10:00:00Z",
          read: true,
        },
      ],
      page: 1,
      pageSize: 50,
      total: 1,
    });
    vi.spyOn(chatApi, "getUserStatus").mockResolvedValue({
      userId: "user-alice",
      isOnline: true,
    });

    renderChatPage();

    await waitFor(() => {
      expect(screen.getByTestId("chat-window")).toBeInTheDocument();
      expect(screen.getAllByText("Alice Chen").length).toBeGreaterThanOrEqual(
        1,
      );
      expect(screen.getByText("Hello from Alice!")).toBeInTheDocument();
    });
  });

  it("renders message thread skeleton while fetching history", async () => {
    vi.spyOn(usersApi, "getUserFollowing").mockResolvedValue(MOCK_FOLLOWING);
    vi.spyOn(chatApi, "getChatHistory").mockReturnValue(new Promise(() => {}));

    renderChatPage();

    await waitFor(() => {
      expect(screen.getByTestId("chat-window")).toBeInTheDocument();
      expect(screen.getByTestId("chat-messages-loading")).toBeInTheDocument();
    });
  });

  it("sends a new message in the chat window", async () => {
    vi.spyOn(usersApi, "getUserFollowing").mockResolvedValue(MOCK_FOLLOWING);
    vi.spyOn(chatApi, "getChatHistory").mockResolvedValue({
      data: [],
      page: 1,
      pageSize: 50,
      total: 0,
    });

    renderChatPage();

    await waitFor(() => {
      expect(screen.getByTestId("chat-messages-empty")).toBeInTheDocument();
    });

    const input = screen.getByPlaceholderText("Message Alice...");
    fireEvent.change(input, { target: { value: "Hello from Vitest!" } });

    const sendBtn = screen.getByTestId("chat-send-btn");
    fireEvent.click(sendBtn);

    await waitFor(() => {
      expect(
        screen.getAllByText("Hello from Vitest!").length,
      ).toBeGreaterThanOrEqual(1);
    });
  });

  it("opens direct conversation when userId and username query params are provided", async () => {
    vi.spyOn(usersApi, "getUserFollowing").mockResolvedValue([]);
    vi.spyOn(chatApi, "getChatHistory").mockResolvedValue({
      data: [],
      page: 1,
      pageSize: 50,
      total: 0,
    });

    renderChatPage({
      initialEntries: ["/chat?userId=target-123&username=TargetUser"],
    });

    await waitFor(() => {
      expect(screen.getByTestId("chat-window")).toBeInTheDocument();
      expect(screen.getAllByText("TargetUser").length).toBeGreaterThanOrEqual(
        1,
      );
    });
  });

  it("shows the error banner and marks every in-flight message as failed", async () => {
    vi.mocked(useChatWebSocket).mockReturnValue({
      sendMessage: vi.fn().mockReturnValue(true),
      sendTyping: vi.fn(),
      isConnected: true,
      error: null,
    });
    vi.spyOn(usersApi, "getUserFollowing").mockResolvedValue(MOCK_FOLLOWING);
    vi.spyOn(chatApi, "getChatHistory").mockResolvedValue({
      data: [],
      page: 1,
      pageSize: 50,
      total: 0,
    });
    vi.spyOn(chatApi, "getUserStatus").mockResolvedValue({
      userId: "user-alice",
      isOnline: true,
    });

    renderChatPage();

    await waitFor(() => {
      expect(screen.getByTestId("chat-messages-empty")).toBeInTheDocument();
    });
    // Flush in-flight fetch continuations: the empty state also renders on
    // first paint, before the initial history fetch settles and rewrites it.
    await act(async () => {});

    const input = screen.getByPlaceholderText("Message Alice...");
    fireEvent.change(input, { target: { value: "First" } });
    fireEvent.click(screen.getByTestId("chat-send-btn"));
    fireEvent.change(input, { target: { value: "Second" } });
    fireEvent.click(screen.getByTestId("chat-send-btn"));

    await waitFor(() => {
      expect(screen.getAllByTestId("status-sent")).toHaveLength(2);
    });

    const calls = vi.mocked(useChatWebSocket).mock.calls;
    const hookOptions = calls[calls.length - 1][0];

    act(() => {
      hookOptions.onError?.("WebSocket connection error");
    });

    expect(screen.getByTestId("chat-error-banner")).toHaveTextContent(
      "WebSocket connection error",
    );
    expect(screen.getAllByTestId("status-failed")).toHaveLength(2);
  });

  it("auto-dismisses the error banner after five seconds", async () => {
    vi.mocked(useChatWebSocket).mockReturnValue({
      sendMessage: vi.fn().mockReturnValue(true),
      sendTyping: vi.fn(),
      isConnected: true,
      error: null,
    });
    vi.spyOn(usersApi, "getUserFollowing").mockResolvedValue(MOCK_FOLLOWING);
    vi.spyOn(chatApi, "getChatHistory").mockResolvedValue({
      data: [],
      page: 1,
      pageSize: 50,
      total: 0,
    });
    vi.spyOn(chatApi, "getUserStatus").mockResolvedValue({
      userId: "user-alice",
      isOnline: true,
    });

    renderChatPage();

    await waitFor(() => {
      expect(screen.getByTestId("chat-messages-empty")).toBeInTheDocument();
    });

    vi.useFakeTimers();
    try {
      const calls = vi.mocked(useChatWebSocket).mock.calls;
      const hookOptions = calls[calls.length - 1][0];

      act(() => {
        hookOptions.onError?.("WebSocket connection error");
      });
      expect(screen.getByTestId("chat-error-banner")).toBeInTheDocument();

      act(() => {
        vi.advanceTimersByTime(5000);
      });
      expect(screen.queryByTestId("chat-error-banner")).not.toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });

  it("registers sidebar unread count for background conversations", async () => {
    useUnreadMessagesStore.getState().resetAll();
    vi.mocked(useChatWebSocket).mockReturnValue({
      sendMessage: vi.fn().mockReturnValue(true),
      sendTyping: vi.fn(),
      isConnected: true,
      error: null,
    });
    vi.spyOn(usersApi, "getUserFollowing").mockResolvedValue(MOCK_FOLLOWING);
    vi.spyOn(chatApi, "getChatHistory").mockResolvedValue({
      data: [],
      page: 1,
      pageSize: 50,
      total: 0,
    });
    vi.spyOn(chatApi, "getUserStatus").mockResolvedValue({
      userId: "user-alice",
      isOnline: true,
    });

    renderChatPage();

    await waitFor(() => {
      expect(screen.getByTestId("chat-messages-empty")).toBeInTheDocument();
    });
    await act(async () => {});

    // Alice's conversation is active, Jonas' runs in the background.
    const calls = vi.mocked(useChatWebSocket).mock.calls;
    const hookOptions = calls[calls.length - 1][0];

    act(() => {
      hookOptions.onMessageReceived?.({
        id: "m-bg",
        senderId: "user-jonas",
        recipientId: "user-current",
        content: "Hey from background",
        sentAt: "2026-10-02T10:00:00Z",
      });
    });

    expect(useUnreadMessagesStore.getState().totalUnread).toBe(1);

    act(() => {
      hookOptions.onMessageReceived?.({
        id: "m-active",
        senderId: "user-alice",
        recipientId: "user-current",
        content: "Hey from active chat",
        sentAt: "2026-10-02T10:01:00Z",
      });
    });

    expect(useUnreadMessagesStore.getState().totalUnread).toBe(1);
    useUnreadMessagesStore.getState().resetAll();
  });

  it("handles mobile master-detail navigation between conversation list and chat window", async () => {
    vi.spyOn(usersApi, "getUserFollowing").mockResolvedValue(MOCK_FOLLOWING);
    vi.spyOn(chatApi, "getChatHistory").mockResolvedValue({
      data: [],
      page: 1,
      pageSize: 50,
      total: 0,
    });

    renderChatPage();

    await waitFor(() => {
      expect(
        screen.getByTestId("chat-conversations-panel"),
      ).toBeInTheDocument();
      expect(screen.getByTestId("chat-window-panel")).toBeInTheDocument();
      expect(
        screen.getByTestId("conversation-item-conv-user-alice"),
      ).toBeInTheDocument();
    });

    // When clicking a conversation in the list
    const aliceBtn = screen.getByTestId("conversation-item-conv-user-alice");
    fireEvent.click(aliceBtn);

    await waitFor(() => {
      expect(screen.getByTestId("chat-window")).toBeInTheDocument();
    });

    // Back button should be present in ChatHeader
    const backBtn = screen.getByTestId("chat-back-btn");
    expect(backBtn).toBeInTheDocument();

    // Clicking back toggles isMobileViewingChat back to false
    fireEvent.click(backBtn);
    expect(screen.getByTestId("chat-conversations-panel")).not.toHaveClass(
      "hidden",
    );
  });
});
