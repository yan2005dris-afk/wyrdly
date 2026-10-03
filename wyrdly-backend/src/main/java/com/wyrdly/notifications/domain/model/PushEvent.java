package com.wyrdly.notifications.domain.model;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * A single Web Push notification to be dispatched to {@code recipientUserId}'s browser.
 *
 * <p>Mirrors the {@code NotificationType} union declared in the frontend
 * (\`wyrdly-frontend/src/features/notifications/types/index.ts\`). The dispatcher will resolve the
 * recipient's active subscription, encrypt the payload, sign a VAPID JWT, and POST to the browser's
 * Push Service URL. Failures (HTTP 404/410) trigger cleanup of the stored subscription.
 */
public record PushEvent(
    String recipientUserId,
    String type,
    String title,
    String body,
    String deepLink,
    Map<String, Object> data) {

  public PushEvent {
    Objects.requireNonNull(recipientUserId, "recipientUserId");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(body, "body");
    if (data == null) {
      data = Collections.emptyMap();
    } else {
      data = Collections.unmodifiableMap(new java.util.LinkedHashMap<>(data));
    }
  }
}
