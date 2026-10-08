package com.wyrdly.notifications.domain.model;

import java.util.Objects;

/**
 * A push recipient with its subscription already resolved, so the dispatcher does not need a
 * per-recipient lookup. Produced in batches by {@code PushAudienceQueryPort}.
 */
public record PushTarget(String userId, PushSubscription subscription) {
  public PushTarget {
    Objects.requireNonNull(userId, "userId");
  }
}
