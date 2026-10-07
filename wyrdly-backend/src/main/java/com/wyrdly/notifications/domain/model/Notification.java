package com.wyrdly.notifications.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * In-app notification persisted in Neo4j. Produced by domain event listeners (follow / reaction)
 * and consumed by the {@code GET /api/notifications} endpoint so the bell-shaped popover can show
 * real entries instead of placeholder data.
 *
 * <p>The shape mirrors what the frontend renders: it carries enough context (deep link, actor id,
 * target resource id) to drive the {@code NotificationItem} component directly.
 *
 * <p>{@link #type} matches the frontend {@code NotificationType} union ({@code POST_LIKE |
 * POST_LOVE | POST_CELEBRATE | GRAPH_FOLLOW | CHAT_MESSAGE}).
 */
public record Notification(
    String id,
    String recipientUserId,
    String type,
    String actorId,
    String title,
    String body,
    String deepLink,
    String targetResourceId,
    boolean isRead,
    Instant createdAt) {

  public Notification {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(recipientUserId, "recipientUserId");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(actorId, "actorId");
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(createdAt, "createdAt");
  }
}
