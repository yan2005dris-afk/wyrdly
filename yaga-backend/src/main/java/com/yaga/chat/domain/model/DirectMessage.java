package com.yaga.chat.domain.model;

import com.yaga.chat.domain.exception.InvalidMessageException;
import java.time.Instant;
import java.util.UUID;

public class DirectMessage {
  private final String id;
  private final String senderId;
  private final String recipientId;
  private final String content;
  private final Instant sentAt;

  public DirectMessage(
      String senderId,
      String recipientId,
      String content) {
    this.id = UUID.randomUUID().toString();
    this.senderId = senderId;
    this.recipientId = recipientId;
    this.content = content;
    this.sentAt = Instant.now();
  }

  public DirectMessage(
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

  public void validateContent() {
    if (content == null || content.trim().isEmpty()) {
      throw new InvalidMessageException("Message content cannot be empty");
    }
    if (content.length() > 5000) {
      throw new InvalidMessageException("Message content exceeds 5000 characters");
    }
  }

  public void validateRecipient() {
    if (senderId.equals(recipientId)) {
      throw new InvalidMessageException("Cannot send message to yourself");
    }
  }
}
