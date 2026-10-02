package com.wyrdly.chat.interfaces.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wyrdly.chat.application.dto.MessageResponse;
import com.wyrdly.chat.application.service.JwtValidationService;
import com.wyrdly.chat.application.usecase.SendMessageUseCase;
import com.wyrdly.chat.infrastructure.websocket.ChatSessionRegistry;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import java.io.IOException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
class ChatWebSocketEndpointTest {

  @Inject ChatWebSocketEndpoint endpoint;

  @InjectMock ChatSessionRegistry sessionRegistry;

  @InjectMock SendMessageUseCase sendMessageUseCase;

  @InjectMock JwtValidationService jwtValidationService;

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldExtractUserIdFromToken() throws Exception {
    String validToken = "valid.jwt.token";

    Mockito.when(jwtValidationService.validateAndExtractUserId(validToken)).thenReturn("user123");

    Session mockSession = Mockito.mock(Session.class);
    Mockito.when(mockSession.getQueryString()).thenReturn("token=" + validToken);
    Mockito.when(mockSession.getId()).thenReturn("session-123");
    Mockito.when(mockSession.isOpen()).thenReturn(true);
    Mockito.when(mockSession.getAsyncRemote()).thenReturn(Mockito.mock(RemoteEndpoint.Async.class));

    endpoint.onOpen(mockSession);

    Mockito.verify(sessionRegistry).register("user123", mockSession);
  }

  @Test
  void shouldHandleInvalidTokenWith4401() throws IOException {
    Session mockSession = Mockito.mock(Session.class);
    Mockito.when(mockSession.getQueryString()).thenReturn("token=invalid");
    Mockito.when(mockSession.getId()).thenReturn("session-123");

    Mockito.when(jwtValidationService.validateAndExtractUserId("invalid")).thenReturn(null);

    endpoint.onOpen(mockSession);

    Mockito.verify(mockSession)
        .close(Mockito.argThat(reason -> reason.getCloseCode().getCode() == 4401));
  }

  @Test
  void shouldUnregisterSessionOnClose() {
    Session mockSession = Mockito.mock(Session.class);
    Mockito.when(mockSession.getId()).thenReturn("session-123");

    endpoint.onClose(mockSession);

    Mockito.verify(sessionRegistry).unregister("session-123");
  }

  @Test
  void shouldHandleSendMessageAction() throws Exception {
    Session mockSession = Mockito.mock(Session.class);
    Mockito.when(mockSession.getId()).thenReturn("session-123");
    Mockito.when(mockSession.isOpen()).thenReturn(true);
    Mockito.when(mockSession.getAsyncRemote()).thenReturn(Mockito.mock(RemoteEndpoint.Async.class));

    Mockito.when(sessionRegistry.getUserForSession("session-123")).thenReturn("user1");
    Mockito.when(sessionRegistry.isUserOnline("user2")).thenReturn(true);

    MessageResponse mockResponse =
        new MessageResponse("msg-1", "user1", "user2", "Hello", Instant.now());
    Mockito.when(sendMessageUseCase.execute("user1", "user2", "Hello")).thenReturn(mockResponse);

    String payload =
        "{\"action\":\"SEND_MESSAGE\",\"recipientId\":\"user2\",\"content\":\"Hello\"}";
    endpoint.onMessage(payload, mockSession);

    Mockito.verify(sendMessageUseCase).execute("user1", "user2", "Hello");
    Mockito.verify(sessionRegistry).broadcast(Mockito.eq("user2"), Mockito.contains("NEW_MESSAGE"));
  }
}
