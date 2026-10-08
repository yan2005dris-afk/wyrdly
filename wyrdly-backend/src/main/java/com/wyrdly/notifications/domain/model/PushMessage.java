package com.wyrdly.notifications.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Recipient-agnostic Web Push template. A fan-out (one message, many recipients) builds a single
 * {@code PushMessage} and binds it to each recipient with {@link #toEvent(String)}.
 */
public record PushMessage(
    String type, String title, String body, String deepLink, Map<String, Object> data) {

  public PushMessage {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(body, "body");
    data =
        data == null
            ? Collections.emptyMap()
            : Collections.unmodifiableMap(new LinkedHashMap<>(data));
  }

  /** Binds this template to a concrete recipient. */
  public PushEvent toEvent(String recipientUserId) {
    return new PushEvent(recipientUserId, type, title, body, deepLink, data);
  }
}
