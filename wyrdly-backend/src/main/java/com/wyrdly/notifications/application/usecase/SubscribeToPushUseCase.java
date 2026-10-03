package com.wyrdly.notifications.application.usecase;

import com.wyrdly.notifications.application.dto.SubscribeRequestDto;
import com.wyrdly.notifications.application.dto.SubscribeResponseDto;

/** Input port for the subscription registration flow. */
public interface SubscribeToPushUseCase {
  SubscribeResponseDto subscribe(String userId, SubscribeRequestDto request);
}
