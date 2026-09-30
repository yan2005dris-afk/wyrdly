import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ConversationList, ConversationItem } from "./ConversationList";
import type { ChatConversation } from "../../types";

const MOCK_CONVERSATIONS: ChatConversation[] = [
  {
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
    lastMessage: {
      id: "msg-1",
      senderId: "user-alice",
      recipientId: "user-maya",
      text: "Did you see the new relay map?",
      timestamp: "2m",
      deliveryStatus: "DELIVERED",
      isEncrypted: true,
    },
    unreadCount: 2,
    isOnline: true,
  },
];

describe("ConversationList & ConversationItem Components", () => {
  it("renders conversation items, unread badges and timestamps", () => {
    render(
      <ConversationList
        conversations={MOCK_CONVERSATIONS}
        onSelectConversation={vi.fn()}
      />,
    );

    expect(screen.getByText("Alice Chen")).toBeInTheDocument();
    expect(
      screen.getByText("Did you see the new relay map?"),
    ).toBeInTheDocument();
    expect(screen.getByTestId("unread-badge-conv-1")).toHaveTextContent("2");
    expect(screen.getByText("2m")).toBeInTheDocument();
  });

  it("fires onSelectConversation when item is clicked", () => {
    const handleSelect = vi.fn();
    render(
      <ConversationItem
        conversation={MOCK_CONVERSATIONS[0]}
        onClick={handleSelect}
      />,
    );

    const item = screen.getByTestId("conversation-item-conv-1");
    fireEvent.click(item);
    expect(handleSelect).toHaveBeenCalledWith("conv-1");
  });

  it("filters conversations based on search query", () => {
    render(
      <ConversationList
        conversations={MOCK_CONVERSATIONS}
        searchQuery="No Match Query"
        onSelectConversation={vi.fn()}
      />,
    );

    expect(screen.getByText("No conversations found")).toBeInTheDocument();
  });
});
