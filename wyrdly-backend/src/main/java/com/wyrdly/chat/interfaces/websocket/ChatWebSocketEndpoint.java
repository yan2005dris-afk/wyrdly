package com.wyrdly.chat.interfaces.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wyrdly.chat.application.dto.MessageRequest;
import com.wyrdly.chat.application.dto.MessageResponse;
import com.wyrdly.chat.application.service.JwtValidationService;
import com.wyrdly.chat.application.usecase.SendMessageUseCase;
import com.wyrdly.chat.domain.model.MessageAction;
import com.wyrdly.chat.infrastructure.websocket.ChatSessionRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

@ServerEndpoint("/ws/chat")
@ApplicationScoped
public class ChatWebSocketEndpoint {

  private static final Logger LOGGER = Logger.getLogger(ChatWebSocketEndpoint.class.getName());

  @Inject ObjectMapper objectMapper;

  @Inject ChatSessionRegistry sessionRegistry;

  @Inject SendMessageUseCase sendMessageUseCase;

  @Inject JwtValidationService jwtValidationService;

  private static final CloseReason.CloseCode CLOSE_UNAUTHORIZED =
      new CloseReason.CloseCode() {
        @Override
        public int getCode() {
          return 4401;
        }
      };

  @OnOpen
  public void onOpen(Session session) {
    try {
      String token = extractTokenFromQuery(session);
      if (token == null || token.isEmpty()) {
        session.close(new CloseReason(CLOSE_UNAUTHORIZED, "Missing token"));
        return;
      }

      String userId = validateAndExtractUserId(token);
      if (userId == null) {
        LOGGER.warning("WebSocket: Invalid or unverified JWT token");
        session.close(new CloseReason(CLOSE_UNAUTHORIZED, "Invalid token"));
        return;
      }

      sessionRegistry.register(userId, session);

      Map<String, Object> response = new HashMap<>();
      response.put("action", "CONNECTION_ESTABLISHED");
      response.put("userId", userId);
      response.put("message", "Connected to chat server");
      sendJson(session, response);

      LOGGER.info("WebSocket opened for user: " + userId);
    } catch (Exception e) {
      LOGGER.warning("Error on WebSocket open: " + e.getMessage());
      try {
        session.close(
            new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION, "Internal error"));
      } catch (IOException ex) {
        LOGGER.warning("Error closing session: " + ex.getMessage());
      }
    }
  }

  @OnMessage
  public void onMessage(String message, Session session) {
    try {
      String userId = sessionRegistry.getUserForSession(session.getId());
      if (userId == null) {
        session.close(new CloseReason(CLOSE_UNAUTHORIZED, "Unauthorized"));
        return;
      }

      MessageRequest request = objectMapper.readValue(message, MessageRequest.class);

      if (MessageAction.SEND_MESSAGE.name().equals(request.getAction())) {
        handleSendMessage(userId, request, session);
      } else if (MessageAction.TYPING.name().equals(request.getAction())) {
        handleTyping(userId, request.getRecipientId(), session);
      }
    } catch (Exception e) {
      LOGGER.warning("Error processing message: " + e.getMessage());
      e.printStackTrace();
      try {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("action", "ERROR");
        errorResponse.put(
            "message", e.getMessage() != null ? e.getMessage() : "Invalid message format");
        sendJson(session, errorResponse);
      } catch (IOException ex) {
        LOGGER.warning("Error sending error response: " + ex.getMessage());
      }
    }
  }

  @OnClose
  public void onClose(Session session) {
    sessionRegistry.unregister(session.getId());
    LOGGER.info("WebSocket closed for session: " + session.getId());
  }

  @OnError
  public void onError(Session session, Throwable throwable) {
    LOGGER.warning("WebSocket error: " + throwable.getMessage());
    sessionRegistry.unregister(session.getId());
  }

  private void handleSendMessage(String senderId, MessageRequest request, Session session)
      throws IOException {
    try {
      MessageResponse response =
          sendMessageUseCase.execute(senderId, request.getRecipientId(), request.getContent());

      Map<String, Object> msgResponse = new HashMap<>();
      msgResponse.put("action", "MESSAGE_SENT");
      msgResponse.put("message", response);
      sendJson(session, msgResponse);

      if (sessionRegistry.isUserOnline(request.getRecipientId())) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("action", "NEW_MESSAGE");
        notification.put("message", response);
        sessionRegistry.broadcast(request.getRecipientId(), serializeToJson(notification));
      }

      LOGGER.info("Message sent from " + senderId + " to " + request.getRecipientId());
    } catch (Exception e) {
      LOGGER.warning("Error sending message: " + e.getMessage());
      Map<String, Object> errorResponse = new HashMap<>();
      errorResponse.put("action", "ERROR");
      errorResponse.put("message", e.getMessage());
      sendJson(session, errorResponse);
    }
  }

  private void handleTyping(String senderId, String recipientId, Session session)
      throws IOException {
    Map<String, Object> typingNotification = new HashMap<>();
    typingNotification.put("action", "USER_TYPING");
    typingNotification.put("userId", senderId);
    sessionRegistry.broadcastExcept(
        recipientId, serializeToJson(typingNotification), session.getId());
  }

  private String extractTokenFromQuery(Session session) {
    String query = session.getQueryString();
    if (query == null) {
      return null;
    }
    try {
      String[] params = query.split("&");
      for (String param : params) {
        if (param.startsWith("token=")) {
          return java.net.URLDecoder.decode(param.substring(6), StandardCharsets.UTF_8);
        }
      }
    } catch (Exception e) {
      LOGGER.warning("Error extracting token: " + e.getMessage());
    }
    return null;
  }

  /**
   * Valida JWT y extrae userId. Verifica firma criptográfica y claims requeridos. ✅ Fix #1:
   * Previene JWT tamperizado
   *
   * @return userId si JWT es válido, null si inválido o firma falsa
   */
  private String validateAndExtractUserId(String token) {
    try {
      return jwtValidationService.validateAndExtractUserId(token);
    } catch (Exception e) {
      LOGGER.warning("JWT validation error: " + e.getMessage());
      return null;
    }
  }

  private void sendJson(Session session, Object object) throws IOException {
    if (session == null || session.getAsyncRemote() == null) {
      LOGGER.warning("Cannot send message: session or async remote is null");
      return;
    }
    session.getAsyncRemote().sendText(serializeToJson(object));
  }

  private String serializeToJson(Object object) throws IOException {
    return objectMapper.writeValueAsString(object);
  }
}
