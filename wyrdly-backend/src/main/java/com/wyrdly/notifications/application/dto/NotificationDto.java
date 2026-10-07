package com.wyrdly.notifications.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wyrdly.notifications.domain.model.Notification;
import java.time.Instant;

/**
 * Public projection of a {@link Notification} enriched with actor info. Mirrors the shape the
 * frontend {@code SocialNotification} component expects (see {@code
 * wyrdly-frontend/src/features/notifications/types/index.ts}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationDto(
    String id,
    String type,
    String title,
    String body,
    String deepLink,
    String targetResourceId,
    boolean isRead,
    Instant createdAt,
    ActorDto actor) {

  public static NotificationDto from(Notification notification, ActorDto actor) {
    return new NotificationDto(
        notification.id(),
        notification.type(),
        notification.title(),
        notification.body(),
        notification.deepLink(),
        notification.targetResourceId(),
        notification.isRead(),
        notification.createdAt(),
        actor);
  }

  /** Minimal actor projection. Real values come from the user profile repository. */
  public record ActorDto(
      String id, String username, String fullName, String avatarUrl, String instanceUrl) {
    public static ActorDto placeholder(String id) {
      return new ActorDto(id, null, "Someone", null, "");
    }
  }
}
