package com.wyrdly.chat.domain.model;

import java.time.Instant;

public class ChatSession {
  private final String userId;
  private final String sessionId;
  private final Instant connectedAt;
  private Instant lastActivityAt;

  public ChatSession(String userId, String sessionId) {
    this.userId = userId;
    this.sessionId = sessionId;
    this.connectedAt = Instant.now();
    this.lastActivityAt = Instant.now();
  }

  public String getUserId() {
    return userId;
  }

  public String getSessionId() {
    return sessionId;
  }

  public Instant getConnectedAt() {
    return connectedAt;
  }

  public Instant getLastActivityAt() {
    return lastActivityAt;
  }

  public void updateActivity() {
    this.lastActivityAt = Instant.now();
  }

  public boolean isStale(long inactivityTimeoutMs) {
    return System.currentTimeMillis() - lastActivityAt.toEpochMilli() > inactivityTimeoutMs;
  }
}
