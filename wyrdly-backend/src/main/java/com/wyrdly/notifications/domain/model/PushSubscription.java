package com.wyrdly.notifications.domain.model;

import java.util.Objects;

/**
 * Aggregate of the fields the backend persists on a {@code :Usuario} node to identify a
 * push-enabled browser. The {@code endpoint} is the Push Service URL (e.g. FCM or Mozilla); {@code
 * p256dh} is the client's ECDH public key; {@code auth} is the shared auth secret.
 */
public record PushSubscription(String endpoint, String p256dh, String auth) {
  public PushSubscription {
    Objects.requireNonNull(endpoint, "endpoint");
    Objects.requireNonNull(p256dh, "p256dh");
    Objects.requireNonNull(auth, "auth");
    if (endpoint.isBlank() || p256dh.isBlank() || auth.isBlank()) {
      throw new IllegalArgumentException("endpoint, p256dh, auth must be non-blank");
    }
  }
}
