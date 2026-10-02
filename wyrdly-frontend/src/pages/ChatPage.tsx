import { useState, useEffect, useCallback, useMemo, type FC } from "react";
import { useSearchParams } from "react-router-dom";
import type {
  ChatConversation,
  ChatMessage,
  MessageResponse,
} from "../features/chat";
import type { UserProfileSummary } from "../types/domain";
import { useAuth } from "../features/auth";
import { usersApi } from "../api/users";
import {
  ConversationList,
  ChatWindow,
  useChatWebSocket,
  chatApi,
} from "../features/chat";

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
  const [searchParams] = useSearchParams();
  const queryUserId = searchParams.get("userId");
  const queryUsername = searchParams.get("username");

  const token =
    typeof window !== "undefined" ? localStorage.getItem("wyrdly_token") : null;
  const currentUserId = user?.id || "user-maya";

  const [conversations, setConversations] = useState<
    readonly ChatConversation[]
  >(INITIAL_CONVERSATIONS);
  const [selectedConvId, setSelectedConvId] = useState<string | null>(null);
  const [messages, setMessages] =
    useState<readonly ChatMessage[]>(INITIAL_MESSAGES);
  const [searchQuery, setSearchQuery] = useState("");

  const directTargetConv: ChatConversation | null = useMemo(() => {
    if (!queryUserId) return null;
    return {
      id: `conv-${queryUserId}`,
      participant: {
        id: queryUserId,
        username: queryUsername || "User",
        fullName: queryUsername || "User",
        avatarUrl: undefined,
        isVerified: false,
        instanceUrl: "wyrdly.social",
        stats: { followersCount: 0, followingCount: 0, postsCount: 0 },
      },
      unreadCount: 0,
      isOnline: true,
    };
  }, [queryUserId, queryUsername]);

  const allConversations = useMemo(() => {
    if (!directTargetConv) return conversations;
    const exists = conversations.some((c) => c.id === directTargetConv.id);
    return exists ? conversations : [directTargetConv, ...conversations];
  }, [conversations, directTargetConv]);

  const activeConvId =
    selectedConvId ||
    (queryUserId
      ? `conv-${queryUserId}`
      : allConversations[0]?.id || "conv-alice");

  const activeConversation =
    allConversations.find((c) => c.id === activeConvId) || allConversations[0];

  // Load followed users into conversations list
  useEffect(() => {
    if (!user?.username) return;

    let isCancelled = false;
    usersApi
      .getUserFollowing(user.username, { pageSize: 50 })
      .then((followingUsers) => {
        if (isCancelled) return;
        const loadedConvs: ChatConversation[] = followingUsers.map((u) => ({
          id: `conv-${u.id}`,
          participant: {
            id: u.id,
            username: u.username,
            fullName: u.fullName,
            avatarUrl: u.avatarUrl ?? undefined,
            isVerified: false,
            instanceUrl: "wyrdly.social",
            stats: { followersCount: 0, followingCount: 0, postsCount: 0 },
          },
          unreadCount: 0,
          isOnline: true,
        }));

        if (loadedConvs.length > 0) {
          setConversations(loadedConvs);
        }
      })
      .catch(() => {
        // Fallback to initial conversations if network fails
      });

    return () => {
      isCancelled = true;
    };
  }, [user?.username]);

  const handleIncomingMessage = useCallback(
    (incoming: MessageResponse) => {
      const newMsg: ChatMessage = {
        id: incoming.id,
        senderId: incoming.senderId,
        recipientId: incoming.recipientId,
        text: incoming.content,
        timestamp: new Date(incoming.sentAt).toLocaleTimeString([], {
          hour: "2-digit",
          minute: "2-digit",
        }),
        deliveryStatus: "DELIVERED",
        isEncrypted: true,
      };

      setMessages((prev) => [...prev, newMsg]);

      setConversations((prev) =>
        prev.map((c) =>
          c.participant.id === incoming.senderId
            ? {
                ...c,
                lastMessage: newMsg,
                unreadCount: c.id === activeConvId ? 0 : c.unreadCount + 1,
              }
            : c,
        ),
      );
    },
    [activeConvId],
  );

  const { isConnected, sendMessage } = useChatWebSocket({
    token,
    onMessageReceived: handleIncomingMessage,
  });

  // Fetch real chat history if recipient exists
  useEffect(() => {
    if (!token || !activeConversation?.participant?.id) return;
    const recipientId = activeConversation.participant.id;
    let isCancelled = false;

    // Only attempt fetch if not mock or if backend is reachable
    chatApi
      .getChatHistory(recipientId, 1, 50)
      .then((history) => {
        if (isCancelled) return;
        if (history?.data && history.data.length > 0) {
          const loadedMessages: ChatMessage[] = history.data.map((m) => ({
            id: m.id,
            senderId: m.senderId,
            recipientId: m.recipientId,
            text: m.content,
            timestamp: new Date(m.sentAt).toLocaleTimeString([], {
              hour: "2-digit",
              minute: "2-digit",
            }),
            deliveryStatus: "READ",
            isEncrypted: true,
          }));
          setMessages(loadedMessages);
        } else {
          setMessages(recipientId === "user-alice" ? INITIAL_MESSAGES : []);
        }
      })
      .catch(() => {
        if (isCancelled) return;
        setMessages(recipientId === "user-alice" ? INITIAL_MESSAGES : []);
      });

    return () => {
      isCancelled = true;
    };
  }, [token, activeConversation?.participant?.id]);

  const handleSendMessage = (text: string) => {
    if (!activeConversation?.participant?.id) return;
    const recipientId = activeConversation.participant.id;

    // Try sending over WebSocket
    const sentViaWs = sendMessage(recipientId, text);

    const newMsg: ChatMessage = {
      id: `msg-${Date.now()}`,
      senderId: currentUserId,
      recipientId,
      text,
      timestamp: "Just now",
      deliveryStatus: sentViaWs ? "SENT" : "DELIVERED",
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
          conversations={allConversations}
          activeConversationId={activeConvId}
          searchQuery={searchQuery}
          onSearchChange={setSearchQuery}
          onSelectConversation={setSelectedConvId}
        />
      </div>

      <div className="lg:col-span-7 h-full overflow-hidden">
        {activeConversation ? (
          <ChatWindow
            conversation={{
              ...activeConversation,
              isOnline: isConnected ? true : activeConversation.isOnline,
            }}
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
