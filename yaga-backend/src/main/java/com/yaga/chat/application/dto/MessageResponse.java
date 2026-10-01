package com.yaga.chat.application.dto;

import java.time.Instant;

public class MessageResponse {
  private String id;
  private String senderId;
  private String recipientId;
  private String content;
  private Instant sentAt;

  public MessageResponse() {}

  public MessageResponse(
      String id,
      String senderId,
      String recipientId,
      String content,
      Instant sentAt) {
    this.id = id;
    this.senderId = senderId;
    this.recipientId = recipientId;
    this.content = content;
    this.sentAt = sentAt;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getSenderId() {
    return senderId;
  }

  public void setSenderId(String senderId) {
    this.senderId = senderId;
  }

  public String getRecipientId() {
    return recipientId;
  }

  public void setRecipientId(String recipientId) {
    this.recipientId = recipientId;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public Instant getSentAt() {
    return sentAt;
  }

  public void setSentAt(Instant sentAt) {
    this.sentAt = sentAt;
  }
}
