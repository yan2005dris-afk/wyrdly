package com.wyrdly.post.domain.event;

import java.time.Instant;
import java.util.Objects;

/**
 * Fired by {@code CommentService} after a comment is persisted. Consumed by the notifications
 * bounded context to dispatch in-app + Web Push notifications to the post author. {@code
 * postAuthorId} is resolved at fire time so listeners do not need to re-query the graph.
 */
public record CommentCreatedEvent(
    String commentId,
    String postId,
    String postAuthorId,
    String commentAuthorId,
    String content,
    Instant createdAt) {

  public CommentCreatedEvent {
    Objects.requireNonNull(commentId, "commentId must not be null");
    Objects.requireNonNull(postId, "postId must not be null");
    Objects.requireNonNull(postAuthorId, "postAuthorId must not be null");
    Objects.requireNonNull(commentAuthorId, "commentAuthorId must not be null");
    Objects.requireNonNull(content, "content must not be null");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
  }
}
