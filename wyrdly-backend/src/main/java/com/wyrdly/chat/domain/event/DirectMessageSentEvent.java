package com.wyrdly.chat.domain.event;

import java.time.Instant;
import java.util.Objects;

/**
 * Fired by the chat application layer when a direct message is successfully persisted. Consumed by
 * the notifications bounded context ({@code ChatMessagePushEventListener}) to populate the
 * recipient's in-app notification feed and dispatch a Web Push notification when the recipient is
 * not connected via WebSocket.
 *
 * <p>{@code messageId} is captured at fire time so listeners do not need to re-query the
 * repository.
 */
public record DirectMessageSentEvent(
    String messageId, String senderId, String recipientId, String content, Instant sentAt) {

  public DirectMessageSentEvent {
    Objects.requireNonNull(messageId, "messageId must not be null");
    Objects.requireNonNull(senderId, "senderId must not be null");
    Objects.requireNonNull(recipientId, "recipientId must not be null");
    Objects.requireNonNull(content, "content must not be null");
    Objects.requireNonNull(sentAt, "sentAt must not be null");
  }
}
