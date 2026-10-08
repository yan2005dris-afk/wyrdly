import {
  useState,
  useEffect,
  useCallback,
  useMemo,
  useRef,
  type FC,
} from "react";
import { useSearchParams } from "react-router-dom";
import { MessageSquare } from "lucide-react";
import type {
  ChatConversation,
  ChatMessage,
  MessageResponse,
} from "../features/chat";
import { useUnreadMessagesStore } from "../features/chat";
import { useAuth } from "../features/auth";
import { usersApi } from "../api/users";
import {
  ConversationList,
  ChatWindow,
  useChatWebSocket,
  chatApi,
} from "../features/chat";

export const ChatPage: FC = () => {
  const { user, token } = useAuth();
  const [searchParams] = useSearchParams();
  const queryUserId = searchParams.get("userId");
  const queryUsername = searchParams.get("username");

  const currentUserId = user?.id || "";

  const [conversations, setConversations] = useState<
    readonly ChatConversation[]
  >([]);
  const [isLoadingConversations, setIsLoadingConversations] = useState(() =>
    Boolean(user?.username),
  );
  const [isLoadingMessages, setIsLoadingMessages] = useState(false);
  const [selectedConvId, setSelectedConvId] = useState<string | null>(null);
  const [messages, setMessages] = useState<readonly ChatMessage[]>([]);
  const [searchQuery, setSearchQuery] = useState("");
  const [chatError, setChatError] = useState<string | null>(null);
  const errorTimeoutRef = useRef<number | null>(null);

  useEffect(
    () => () => {
      if (errorTimeoutRef.current !== null) {
        window.clearTimeout(errorTimeoutRef.current);
      }
    },
    [],
  );

  const handleDismissError = useCallback(() => {
    if (errorTimeoutRef.current !== null) {
      window.clearTimeout(errorTimeoutRef.current);
      errorTimeoutRef.current = null;
    }
    setChatError(null);
  }, []);

  const handleChatError = useCallback((errMsg: string) => {
    setChatError(errMsg);
    // Every message still in flight failed, not just the last one:
    // with a flaky connection several SENT messages can be pending.
    setMessages((prev) =>
      prev.map((m) =>
        m.deliveryStatus === "SENDING" || m.deliveryStatus === "SENT"
          ? { ...m, deliveryStatus: "FAILED" as const }
          : m,
      ),
    );
    if (errorTimeoutRef.current !== null) {
      window.clearTimeout(errorTimeoutRef.current);
    }
    errorTimeoutRef.current = window.setTimeout(() => {
      errorTimeoutRef.current = null;
      setChatError(null);
    }, 5000);
  }, []);

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
      isOnline: false,
    };
  }, [queryUserId, queryUsername]);

  const allConversations = useMemo(() => {
    if (!directTargetConv) return conversations;
    const exists = conversations.some((c) => c.id === directTargetConv.id);
    return exists ? conversations : [directTargetConv, ...conversations];
  }, [conversations, directTargetConv]);

  const activeConvId =
    selectedConvId ||
    (queryUserId ? `conv-${queryUserId}` : allConversations[0]?.id || null);

  const activeConversation =
    allConversations.find((c) => c.id === activeConvId) ||
    allConversations[0] ||
    null;

  // Load followed users into conversations list
  useEffect(() => {
    if (!user?.username) return;

    let isCancelled = false;

    const fetchFollowing = async () => {
      setIsLoadingConversations(true);
      try {
        const followingUsers = await usersApi.getUserFollowing(user.username, {
          pageSize: 50,
        });
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
          isOnline: false,
        }));
        setConversations(loadedConvs);
      } catch {
        if (isCancelled) return;
        setConversations([]);
      } finally {
        setIsLoadingConversations(false);
      }
    };

    fetchFollowing();

    return () => {
      isCancelled = true;
    };
  }, [user?.username]);

  // Query online status for the active conversation participant
  useEffect(() => {
    if (!token || !activeConversation?.participant?.id) return;
    const recipientId = activeConversation.participant.id;

    let isCancelled = false;
    chatApi
      .getUserStatus(recipientId)
      .then((status) => {
        if (isCancelled) return;
        setConversations((prev) =>
          prev.map((c) =>
            c.participant.id === recipientId
              ? { ...c, isOnline: status.isOnline }
              : c,
          ),
        );
      })
      .catch(() => {
        // Keep existing status on network failure
      });

    return () => {
      isCancelled = true;
    };
  }, [token, activeConversation?.participant?.id]);

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

      // Mirror the per-conversation badge into the global sidebar counter,
      // except when the message arrived in the conversation being viewed.
      const incomingConvId = `conv-${incoming.senderId}`;
      useUnreadMessagesStore
        .getState()
        .registerIncoming(incomingConvId, incomingConvId === activeConvId);

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

  // Opening (or switching to) a conversation clears its sidebar badge.
  useEffect(() => {
    useUnreadMessagesStore.getState().markConversationRead(activeConvId);
  }, [activeConvId]);

  const { sendMessage } = useChatWebSocket({
    token,
    onMessageReceived: handleIncomingMessage,
    onError: handleChatError,
  });

  // Fetch real chat history if recipient exists (sorted chronologically)
  useEffect(() => {
    if (!token || !activeConversation?.participant?.id) return;
    const recipientId = activeConversation.participant.id;
    let isCancelled = false;

    const fetchHistory = async () => {
      setIsLoadingMessages(true);
      try {
        const history = await chatApi.getChatHistory(recipientId, 1, 50);
        if (isCancelled) return;
        if (history?.data && history.data.length > 0) {
          const loadedMessages: ChatMessage[] = [...history.data]
            .sort(
              (a, b) =>
                new Date(a.sentAt).getTime() - new Date(b.sentAt).getTime(),
            )
            .map((m) => ({
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
          setMessages([]);
        }
      } catch {
        if (isCancelled) return;
        setMessages([]);
      } finally {
        setIsLoadingMessages(false);
      }
    };

    fetchHistory();

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
          activeConversationId={activeConvId ?? undefined}
          isLoading={isLoadingConversations}
          searchQuery={searchQuery}
          onSearchChange={setSearchQuery}
          onSelectConversation={setSelectedConvId}
        />
      </div>

      <div className="lg:col-span-7 h-full overflow-hidden flex flex-col">
        {chatError && (
          <div
            className="bg-rose-50 border border-rose-200 text-rose-700 px-4 py-2.5 rounded-lg text-sm mb-3 flex items-center justify-between shrink-0"
            role="alert"
            data-testid="chat-error-banner"
          >
            <span>{chatError}</span>
            <button
              type="button"
              onClick={handleDismissError}
              className="text-rose-500 hover:text-rose-700 font-bold ml-2 leading-none"
              aria-label="Cerrar"
            >
              ×
            </button>
          </div>
        )}
        {activeConversation ? (
          <ChatWindow
            conversation={activeConversation}
            currentUserId={currentUserId}
            messages={messages}
            isLoadingMessages={isLoadingMessages}
            onSendMessage={handleSendMessage}
          />
        ) : (
          <div
            className="flex flex-col items-center justify-center h-full bg-white rounded-2xl border border-slate-200 text-slate-400 p-8 text-center"
            data-testid="chat-empty-selection"
          >
            <MessageSquare className="w-12 h-12 text-slate-300 mb-3" />
            <h3 className="text-base font-semibold text-slate-700">
              No conversation selected
            </h3>
            <p className="text-sm text-slate-500 max-w-sm mt-1">
              Select a user from the list or follow users on Wyrdly to start
              messaging.
            </p>
          </div>
        )}
      </div>
    </div>
  );
};
