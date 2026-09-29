import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ChatWindow } from "./ChatWindow";
import type { ChatConversation, ChatMessage } from "../../../types/chat";

const MOCK_CONV: ChatConversation = {
  id: "conv-1",
  participant: {
    id: "user-alice",
    username: "alice",
    fullName: "Alice Chen",
    avatarUrl: "https://example.com/alice.jpg",
    isVerified: true,
    instanceUrl: "wyrdly.app",
    stats: { followersCount: 10, followingCount: 5, postsCount: 20 },
  },
  unreadCount: 0,
  isOnline: true,
};

const MOCK_MESSAGES: ChatMessage[] = [
  {
    id: "msg-1",
    senderId: "user-alice",
    recipientId: "user-maya",
    text: "Hey! Did you see the new relay map?",
    timestamp: "10:24 AM",
    deliveryStatus: "DELIVERED",
    isEncrypted: true,
  },
  {
    id: "msg-2",
    senderId: "user-maya",
    recipientId: "user-alice",
    text: "Yes! 42ms hop.",
    timestamp: "10:26 AM",
    deliveryStatus: "READ",
    isEncrypted: true,
  },
];

describe("ChatWindow Component", () => {
  it("renders participant header, messages thread, and input bar", () => {
    window.HTMLElement.prototype.scrollIntoView = vi.fn();

    render(
      <ChatWindow
        conversation={MOCK_CONV}
        currentUserId="user-maya"
        messages={MOCK_MESSAGES}
        onSendMessage={vi.fn()}
      />,
    );

    expect(screen.getByText("Alice Chen")).toBeInTheDocument();
    expect(
      screen.getByText("Hey! Did you see the new relay map?"),
    ).toBeInTheDocument();
    expect(screen.getByText("Yes! 42ms hop.")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("Message Alice...")).toBeInTheDocument();
    expect(
      screen.getByText("Today • E2E Encrypted over WebSocket"),
    ).toBeInTheDocument();
  });
});
