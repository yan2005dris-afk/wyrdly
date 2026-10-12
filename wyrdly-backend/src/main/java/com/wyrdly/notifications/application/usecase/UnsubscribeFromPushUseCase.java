package com.wyrdly.notifications.application.usecase;

import com.wyrdly.notifications.application.dto.SubscribeResponseDto;

/** Input port for clearing a user's stored push subscriptions. */
public interface UnsubscribeFromPushUseCase {
  /** Clears every subscription for the user. */
  SubscribeResponseDto unsubscribe(String userId);

  /** Clears only the subscription matching {@code endpoint}. Idempotent. */
  SubscribeResponseDto unsubscribeEndpoint(String userId, String endpoint);
}
