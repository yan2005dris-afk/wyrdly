package com.yaga.chat.interfaces.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yaga.chat.application.service.ChatService;
import com.yaga.chat.infrastructure.websocket.ChatSessionRegistry;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.websocket.Session;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
class ChatWebSocketEndpointTest {

  @Inject
  ChatWebSocketEndpoint endpoint;

  @InjectMock
  ChatSessionRegistry sessionRegistry;

  @InjectMock
  ChatService chatService;

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldExtractUserIdFromToken() throws Exception {
    // Token simple para test: será validado por JwtValidationServiceTestMock
    String validToken = "user123";

    Session mockSession = Mockito.mock(Session.class);
    Mockito.when(mockSession.getQueryString()).thenReturn("token=" + validToken);
    Mockito.when(mockSession.getId()).thenReturn("session-123");
    Mockito.when(mockSession.isOpen()).thenReturn(true);
    Mockito.when(mockSession.getAsyncRemote()).thenReturn(Mockito.mock(jakarta.websocket.RemoteEndpoint.Async.class));

    endpoint.onOpen(mockSession);

    Mockito.verify(sessionRegistry).register("user123", mockSession);
  }

  @Test
  void shouldHandleInvalidToken() throws IOException {
    Session mockSession = Mockito.mock(Session.class);
    Mockito.when(mockSession.getQueryString()).thenReturn("token=invalid");
    Mockito.when(mockSession.getId()).thenReturn("session-123");

    endpoint.onOpen(mockSession);

    Mockito.verify(mockSession).close(Mockito.any());
  }

  @Test
  void shouldUnregisterSessionOnClose() {
    Session mockSession = Mockito.mock(Session.class);
    Mockito.when(mockSession.getId()).thenReturn("session-123");

    endpoint.onClose(mockSession);

    Mockito.verify(sessionRegistry).unregister("session-123");
  }

  @Test
  void shouldProcessMessageRequest() throws Exception {
    Session mockSession = Mockito.mock(Session.class);
    Mockito.when(mockSession.getId()).thenReturn("session-123");
    Mockito.when(sessionRegistry.getUserForSession("session-123")).thenReturn("user1");

    Map<String, String> request = new HashMap<>();
    request.put("action", "SEND_MESSAGE");
    request.put("recipientId", "user2");
    request.put("content", "Hello");

    String messageJson = objectMapper.writeValueAsString(request);

    endpoint.onMessage(messageJson, mockSession);

    Mockito.verify(chatService).sendMessage("user1", "user2", "Hello");
  }

  @Test
  void shouldRejectUnregisteredSession() throws IOException {
    Session mockSession = Mockito.mock(Session.class);
    Mockito.when(mockSession.getId()).thenReturn("unknown-session");
    Mockito.when(sessionRegistry.getUserForSession("unknown-session")).thenReturn(null);

    Map<String, String> request = new HashMap<>();
    request.put("action", "SEND_MESSAGE");

    String messageJson = objectMapper.writeValueAsString(request);

    endpoint.onMessage(messageJson, mockSession);

    Mockito.verify(mockSession).close(Mockito.any());
  }
}
