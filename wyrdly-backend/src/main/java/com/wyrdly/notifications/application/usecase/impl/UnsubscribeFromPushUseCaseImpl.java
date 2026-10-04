package com.wyrdly.notifications.application.usecase.impl;

import com.wyrdly.notifications.application.dto.SubscribeResponseDto;
import com.wyrdly.notifications.application.port.PushSubscriptionRepositoryPort;
import com.wyrdly.notifications.application.usecase.UnsubscribeFromPushUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class UnsubscribeFromPushUseCaseImpl implements UnsubscribeFromPushUseCase {

  private final PushSubscriptionRepositoryPort repository;

  @Inject
  public UnsubscribeFromPushUseCaseImpl(PushSubscriptionRepositoryPort repository) {
    this.repository = repository;
  }

  @Override
  public SubscribeResponseDto unsubscribe(String userId) {
    repository.deleteByUserId(userId);
    return SubscribeResponseDto.unsubscribed();
  }
}
