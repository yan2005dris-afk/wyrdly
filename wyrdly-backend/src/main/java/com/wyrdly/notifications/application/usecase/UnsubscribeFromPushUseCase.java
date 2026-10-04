package com.wyrdly.notifications.application.usecase;

import com.wyrdly.notifications.application.dto.SubscribeResponseDto;

/** Input port for clearing a user's stored push subscription. */
public interface UnsubscribeFromPushUseCase {
  SubscribeResponseDto unsubscribe(String userId);
}
