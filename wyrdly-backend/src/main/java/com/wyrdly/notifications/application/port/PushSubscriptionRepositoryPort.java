package com.wyrdly.notifications.application.port;

import com.wyrdly.notifications.domain.model.PushSubscription;
import java.util.List;

/**
 * Output port for reading and writing a user's browser push subscriptions. Each device owns one
 * {@code :SuscripcionPush} node linked from {@code :Usuario} via {@code :TIENE_SUSCRIPCION}, so
 * enabling push in a second browser never overwrites the first one.
 */
public interface PushSubscriptionRepositoryPort {

  /** Maximum subscriptions kept per user; the oldest beyond this limit is evicted on save. */
  int MAX_SUBSCRIPTIONS_PER_USER = 10;

  /** Returns the most recently created subscription for the user, or {@code null} if none. */
  PushSubscription findByUserId(String userId);

  /** Returns every subscription for the user, most recent first. Empty when none is stored. */
  List<PushSubscription> findAllByUserId(String userId);

  /**
   * Persists (or refreshes) the subscription for the given endpoint. Idempotent — re-subscribing
   * with the same {@code endpoint} replaces the existing keys and refreshes its recency.
   */
  void save(String userId, PushSubscription subscription);

  /** Clears every stored subscription for the user. Safe to call when none is present. */
  void deleteByUserId(String userId);

  /** Clears only the subscription matching {@code endpoint}. Safe to call when absent. */
  void deleteByEndpoint(String userId, String endpoint);

  /** Returns the total count of active push subscriptions in the system. */
  default long countActive() {
    return 0L;
  }
}
