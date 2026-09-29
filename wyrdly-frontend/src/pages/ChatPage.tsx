import { useState, type FC } from "react";
import type { ChatConversation, ChatMessage } from "../types/chat";
import type { UserProfileSummary } from "../types/domain";
import { useAuth } from "../hooks/useAuth";
import { ConversationList, ChatWindow } from "../components/chat";

const MOCK_PARTICIPANTS: readonly UserProfileSummary[] = [
  {
    id: "user-alice",
    username: "alice",
    fullName: "Alice Chen",
    avatarUrl:
      "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=100&auto=format&fit=crop&q=80",
    isVerified: true,
    instanceUrl: "wyrdly.app",
    stats: { followersCount: 120, followingCount: 80, postsCount: 45 },
  },
  {
    id: "user-jonas",
    username: "jonas",
    fullName: "Jonas Weber",
    avatarUrl:
      "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=100&auto=format&fit=crop&q=80",
    isVerified: true,
    instanceUrl: "mastodon.social",
    stats: { followersCount: 4200, followingCount: 650, postsCount: 180 },
  },
];

const INITIAL_CONVERSATIONS: readonly ChatConversation[] = [
  {
    id: "conv-alice",
    participant: MOCK_PARTICIPANTS[0],
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
    typingStatus: "Online • typing via relay eu-west-1...",
  },
  {
    id: "conv-jonas",
    participant: MOCK_PARTICIPANTS[1],
    lastMessage: {
      id: "msg-2",
      senderId: "user-jonas",
      recipientId: "user-maya",
      text: "Jonas: deploy at 18:00 UTC",
      timestamp: "9m",
      deliveryStatus: "READ",
      isEncrypted: true,
    },
    unreadCount: 5,
    isOnline: true,
  },
];

const INITIAL_MESSAGES: readonly ChatMessage[] = [
  {
    id: "m-1",
    senderId: "user-alice",
    recipientId: "user-maya",
    text: "Hey! Did you see the new relay map? Your region just lit up!",
    timestamp: "10:24 AM",
    deliveryStatus: "READ",
    isEncrypted: true,
  },
  {
    id: "m-2",
    senderId: "user-maya",
    recipientId: "user-alice",
    text: "Yes! 42ms from ap-south-1 — fastest federated hop we have ever had.",
    timestamp: "10:26 AM",
    deliveryStatus: "READ",
    isEncrypted: true,
  },
  {
    id: "m-3",
    senderId: "user-alice",
    recipientId: "user-maya",
    text: "Shipping the announcement post now. Can you boost it from your instance?",
    timestamp: "10:27 AM",
    deliveryStatus: "READ",
    isEncrypted: true,
  },
  {
    id: "m-4",
    senderId: "user-maya",
    recipientId: "user-alice",
    text: "On it — boosting + pinning to relay highlights",
    timestamp: "10:28 AM",
    deliveryStatus: "READ",
    isEncrypted: true,
  },
];

export const ChatPage: FC = () => {
  const { user } = useAuth();
  const currentUserId = user?.id || "user-maya";

  const [conversations, setConversations] = useState<
    readonly ChatConversation[]
  >(INITIAL_CONVERSATIONS);
  const [activeConvId, setActiveConvId] = useState<string>("conv-alice");
  const [messages, setMessages] =
    useState<readonly ChatMessage[]>(INITIAL_MESSAGES);
  const [searchQuery, setSearchQuery] = useState("");

  const activeConversation =
    conversations.find((c) => c.id === activeConvId) || conversations[0];

  const handleSendMessage = (text: string) => {
    const newMsg: ChatMessage = {
      id: `msg-${Date.now()}`,
      senderId: currentUserId,
      recipientId: activeConversation.participant.id,
      text,
      timestamp: "Just now",
      deliveryStatus: "SENT",
      isEncrypted: true,
    };

    setMessages((prev) => [...prev, newMsg]);

    setConversations((prev) =>
      prev.map((c) =>
        c.id === activeConvId
          ? {
              ...c,
              lastMessage: newMsg,
              unreadCount: 0,
            }
          : c,
      ),
    );
  };

  return (
    <div
      className="grid grid-cols-1 lg:grid-cols-12 gap-6 w-full h-[calc(100vh-10rem)] min-h-[500px]"
      data-testid="chat-page"
    >
      <div className="lg:col-span-5 h-full overflow-hidden">
        <ConversationList
          conversations={conversations}
          activeConversationId={activeConvId}
          searchQuery={searchQuery}
          onSearchChange={setSearchQuery}
          onSelectConversation={setActiveConvId}
        />
      </div>

      <div className="lg:col-span-7 h-full overflow-hidden">
        {activeConversation ? (
          <ChatWindow
            conversation={activeConversation}
            currentUserId={currentUserId}
            messages={messages}
            onSendMessage={handleSendMessage}
          />
        ) : (
          <div className="flex items-center justify-center h-full bg-white rounded-2xl border border-slate-200 text-slate-400 text-sm">
            Select a conversation to start messaging
          </div>
        )}
      </div>
    </div>
  );
};
