import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { ChatPage } from "./ChatPage";
import { AuthContext } from "../features/auth";
import { usersApi } from "../api/users";
import { chatApi } from "../features/chat";
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
});
