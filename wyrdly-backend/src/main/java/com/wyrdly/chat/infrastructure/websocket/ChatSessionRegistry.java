package com.wyrdly.chat.infrastructure.websocket;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.Session;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@ApplicationScoped
public class ChatSessionRegistry {

  private static final Logger LOGGER = Logger.getLogger(ChatSessionRegistry.class.getName());

  private final Map<String, List<Session>> userSessions = new ConcurrentHashMap<>();
  private final Map<String, String> sessionToUser = new ConcurrentHashMap<>();

  public void register(String userId, Session session) {
    userSessions.computeIfAbsent(userId, k -> new ArrayList<>()).add(session);
    sessionToUser.put(session.getId(), userId);
    LOGGER.info("Registered session " + session.getId() + " for user " + userId);
  }

  public void unregister(String sessionId) {
    String userId = sessionToUser.remove(sessionId);
    if (userId != null) {
      List<Session> sessions = userSessions.get(userId);
      if (sessions != null) {
        sessions.removeIf(s -> s.getId().equals(sessionId));
        if (sessions.isEmpty()) {
          userSessions.remove(userId);
        }
      }
      LOGGER.info("Unregistered session " + sessionId + " for user " + userId);
    }
  }

  public List<Session> getSessionsForUser(String userId) {
    return userSessions.getOrDefault(userId, List.of());
  }

  public boolean isUserOnline(String userId) {
    return !getSessionsForUser(userId).isEmpty();
  }

  public String getUserForSession(String sessionId) {
    return sessionToUser.get(sessionId);
  }

  public void broadcast(String userId, String message) {
    List<Session> sessions = getSessionsForUser(userId);
    for (Session session : sessions) {
      try {
        if (session.isOpen()) {
          session.getAsyncRemote().sendText(message);
        }
      } catch (Exception e) {
        LOGGER.warning("Failed to send message to session " + session.getId() + ": " + e.getMessage());
      }
    }
  }

  public void broadcastExcept(String userId, String message, String excludeSessionId) {
    List<Session> sessions = getSessionsForUser(userId);
    for (Session session : sessions) {
      if (!session.getId().equals(excludeSessionId)) {
        try {
          if (session.isOpen()) {
            session.getAsyncRemote().sendText(message);
          }
        } catch (Exception e) {
          LOGGER.warning("Failed to send message to session " + session.getId() + ": " + e.getMessage());
        }
      }
    }
  }
}
