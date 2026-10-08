import { describe, expect, it, beforeEach } from "vitest";
import { useUnreadMessagesStore } from "./useUnreadMessagesStore";

describe("useUnreadMessagesStore", () => {
  beforeEach(() => {
    useUnreadMessagesStore.getState().resetAll();
  });

  it("starts with zero unread messages", () => {
    const state = useUnreadMessagesStore.getState();
    expect(state.totalUnread).toBe(0);
    expect(state.unreadByConversation).toEqual({});
  });

  it("counts incoming messages for background conversations", () => {
    const { registerIncoming } = useUnreadMessagesStore.getState();

    registerIncoming("conv-alice", false);
    registerIncoming("conv-alice", false);
    registerIncoming("conv-jonas", false);

    const state = useUnreadMessagesStore.getState();
    expect(state.unreadByConversation).toEqual({
      "conv-alice": 2,
      "conv-jonas": 1,
    });
    expect(state.totalUnread).toBe(3);
  });

  it("ignores incoming messages for the active conversation", () => {
    useUnreadMessagesStore.getState().registerIncoming("conv-alice", true);

    const state = useUnreadMessagesStore.getState();
    expect(state.totalUnread).toBe(0);
    expect(state.unreadByConversation).toEqual({});
  });

  it("clears the count when a conversation is opened", () => {
    const { registerIncoming, markConversationRead } =
      useUnreadMessagesStore.getState();

    registerIncoming("conv-alice", false);
    registerIncoming("conv-alice", false);
    registerIncoming("conv-jonas", false);

    markConversationRead("conv-alice");

    const state = useUnreadMessagesStore.getState();
    expect(state.unreadByConversation).toEqual({ "conv-jonas": 1 });
    expect(state.totalUnread).toBe(1);
  });

  it("is a no-op when marking an unknown or null conversation as read", () => {
    const { registerIncoming, markConversationRead } =
      useUnreadMessagesStore.getState();

    registerIncoming("conv-alice", false);

    markConversationRead("conv-unknown");
    markConversationRead(null);

    const state = useUnreadMessagesStore.getState();
    expect(state.totalUnread).toBe(1);
    expect(state.unreadByConversation).toEqual({ "conv-alice": 1 });
  });

  it("resets all counts on logout", () => {
    const { registerIncoming, resetAll } = useUnreadMessagesStore.getState();

    registerIncoming("conv-alice", false);
    resetAll();

    const state = useUnreadMessagesStore.getState();
    expect(state.totalUnread).toBe(0);
    expect(state.unreadByConversation).toEqual({});
  });
});
