package com.wyrdly.chat.application.dto;

import java.time.Instant;

public record MessageResponse(
    String id, String senderId, String recipientId, String content, Instant sentAt) {

  public String getId() {
    return id;
  }

  public String getSenderId() {
    return senderId;
  }

  public String getRecipientId() {
    return recipientId;
  }

  public String getContent() {
    return content;
  }

  public Instant getSentAt() {
    return sentAt;
  }
}
