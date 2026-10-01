package com.yaga.chat.application.service;

import com.yaga.chat.domain.model.ChatSession;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.Session;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@ApplicationScoped
public class SessionManagerService {

  private static final Logger LOGGER =
      Logger.getLogger(SessionManagerService.class.getName());

  private final Map<String, List<SessionWrapper>> userSessions = new ConcurrentHashMap<>();
  private final Map<String, String> sessionToUserId = new ConcurrentHashMap<>();

  private static final long INACTIVITY_TIMEOUT_MS = 5 * 60 * 1000;

  public void registerSession(String userId, Session webSocketSession) {
    String sessionId = webSocketSession.getId();
    ChatSession chatSession = new ChatSession(userId, sessionId);
    SessionWrapper wrapper = new SessionWrapper(chatSession, webSocketSession);

    userSessions.computeIfAbsent(userId, k -> new ArrayList<>()).add(wrapper);
    sessionToUserId.put(sessionId, userId);

    LOGGER.info("Session registered for user: " + userId + ", sessionId: " + sessionId);
  }

  public void unregisterSession(String sessionId) {
    String userId = sessionToUserId.remove(sessionId);
    if (userId != null) {
      List<SessionWrapper> sessions = userSessions.get(userId);
      if (sessions != null) {
        sessions.removeIf(w -> w.chatSession.getSessionId().equals(sessionId));
        if (sessions.isEmpty()) {
          userSessions.remove(userId);
        }
      }
      LOGGER.info("Session unregistered: " + sessionId + " for user: " + userId);
    }
  }

  public List<Session> getActiveSessions(String userId) {
    List<SessionWrapper> wrappers = userSessions.get(userId);
    if (wrappers == null) {
      return List.of();
    }
    return wrappers.stream()
        .map(w -> w.webSocketSession)
        .collect(Collectors.toList());
  }

  public boolean isUserOnline(String userId) {
    return !getActiveSessions(userId).isEmpty();
  }

  public void updateActivity(String sessionId) {
    String userId = sessionToUserId.get(sessionId);
    if (userId != null) {
      List<SessionWrapper> sessions = userSessions.get(userId);
      if (sessions != null) {
        sessions.stream()
            .filter(w -> w.chatSession.getSessionId().equals(sessionId))
            .findFirst()
            .ifPresent(w -> w.chatSession.updateActivity());
      }
    }
  }

  public void cleanupStaleSessions() {
    userSessions.forEach(
        (userId, sessions) ->
            sessions.removeIf(
                w -> w.chatSession.isStale(INACTIVITY_TIMEOUT_MS)));
  }

  private static class SessionWrapper {
    ChatSession chatSession;
    Session webSocketSession;

    SessionWrapper(ChatSession chatSession, Session webSocketSession) {
      this.chatSession = chatSession;
      this.webSocketSession = webSocketSession;
    }
  }
}
