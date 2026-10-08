package com.wyrdly.chat.infrastructure.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatSessionRegistryTest {

  private ChatSessionRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new ChatSessionRegistry();
  }

  @Test
  void returnsEmptySessionsInitially() {
    assertFalse(registry.isUserOnline("usr_1"));
    assertTrue(registry.getSessionsForUser("usr_1").isEmpty());
    assertNull(registry.getUserForSession("sess_1"));
  }

  @Test
  void registersSessionAndTracksUserOnline() {
    Session mockSession = mock(Session.class);
    when(mockSession.getId()).thenReturn("sess_1");

    registry.register("usr_1", mockSession);

    assertTrue(registry.isUserOnline("usr_1"));
    assertEquals("usr_1", registry.getUserForSession("sess_1"));
    assertEquals(1, registry.getSessionsForUser("usr_1").size());
  }

  @Test
  void handlesMultipleSessionsForSameUser() {
    Session session1 = mock(Session.class);
    when(session1.getId()).thenReturn("sess_1");
    Session session2 = mock(Session.class);
    when(session2.getId()).thenReturn("sess_2");

    registry.register("usr_1", session1);
    registry.register("usr_1", session2);

    assertTrue(registry.isUserOnline("usr_1"));
    assertEquals(2, registry.getSessionsForUser("usr_1").size());

    registry.unregister("sess_1");
    assertTrue(registry.isUserOnline("usr_1"));
    assertEquals(1, registry.getSessionsForUser("usr_1").size());

    registry.unregister("sess_2");
    assertFalse(registry.isUserOnline("usr_1"));
    assertNull(registry.getUserForSession("sess_2"));
  }

  @Test
  void unregisterNonExistentSessionDoesNotThrow() {
    registry.unregister("non_existent_session");
    assertFalse(registry.isUserOnline("non_existent_user"));
  }

  @Test
  void broadcastsMessageToOpenSessions() {
    Session session = mock(Session.class);
    RemoteEndpoint.Async asyncRemote = mock(RemoteEndpoint.Async.class);
    when(session.getId()).thenReturn("sess_1");
    when(session.isOpen()).thenReturn(true);
    when(session.getAsyncRemote()).thenReturn(asyncRemote);

    registry.register("usr_1", session);
    registry.broadcast("usr_1", "test payload");

    verify(asyncRemote).sendText("test payload");
  }
}
