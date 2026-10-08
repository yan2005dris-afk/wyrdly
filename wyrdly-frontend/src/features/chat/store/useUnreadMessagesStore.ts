import { create } from "zustand";

interface UnreadMessagesState {
  readonly unreadByConversation: Readonly<Record<string, number>>;
  readonly totalUnread: number;
  readonly registerIncoming: (
    conversationId: string,
    isActive: boolean,
  ) => void;
  readonly markConversationRead: (conversationId: string | null) => void;
  readonly resetAll: () => void;
}

/**
 * Client-side unread counter for chat messages arriving over the WebSocket.
 *
 * This is intentionally NOT persisted: without a backend unread endpoint
 * the counts are only valid for the current session, and showing a stale
 * persisted badge would be worse than showing none. The day a
 * `GET /chat/unread-count` endpoint exists, this store becomes the
 * client cache seeded from it.
 */
export const useUnreadMessagesStore = create<UnreadMessagesState>((set) => ({
  unreadByConversation: {},
  totalUnread: 0,

  registerIncoming: (conversationId, isActive) =>
    set((state) => {
      if (isActive) return state;
      return {
        unreadByConversation: {
          ...state.unreadByConversation,
          [conversationId]:
            (state.unreadByConversation[conversationId] ?? 0) + 1,
        },
        totalUnread: state.totalUnread + 1,
      };
    }),

  markConversationRead: (conversationId) =>
    set((state) => {
      if (!conversationId) return state;
      const current = state.unreadByConversation[conversationId] ?? 0;
      if (current === 0) return state;
      const unreadByConversation = { ...state.unreadByConversation };
      delete unreadByConversation[conversationId];
      return {
        unreadByConversation,
        totalUnread: Math.max(0, state.totalUnread - current),
      };
    }),

  resetAll: () => set({ unreadByConversation: {}, totalUnread: 0 }),
}));
