package com.wyrdly.notifications.application.port;

import com.wyrdly.notifications.domain.model.PushSubscription;

/**
 * Output port for reading and writing the {@code pushEndpoint / pushP256dh / pushAuth} triple on a
 * {@code :Usuario} node.
 */
public interface PushSubscriptionRepositoryPort {

  /** Returns the current subscription for the user, or {@code null} if none is stored. */
  PushSubscription findByUserId(String userId);

  /**
   * Persists (or overwrites) the subscription on the user's node. Idempotent — re-subscribing with
   * the same {@code endpoint} replaces the existing keys.
   */
  void save(String userId, PushSubscription subscription);

  /** Clears any stored subscription. Safe to call when none is present. */
  void deleteByUserId(String userId);

  /** Returns the total count of active push subscriptions in the system. */
  default long countActive() {
    return 0L;
  }
}
