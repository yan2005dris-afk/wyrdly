package com.wyrdly.notifications.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.wyrdly.notifications.application.dto.SubscribeResponseDto;
import com.wyrdly.notifications.application.port.PushSubscriptionRepositoryPort;
import com.wyrdly.notifications.application.usecase.impl.UnsubscribeFromPushUseCaseImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UnsubscribeFromPushUseCaseImplTest {

  private PushSubscriptionRepositoryPort repo;
  private UnsubscribeFromPushUseCaseImpl useCase;

  @BeforeEach
  void setUp() {
    repo = org.mockito.Mockito.mock(PushSubscriptionRepositoryPort.class);
    useCase = new UnsubscribeFromPushUseCaseImpl(repo);
  }

  @Test
  void delegatesToRepositoryAndReturnsUnsubscribed() {
    SubscribeResponseDto response = useCase.unsubscribe("usr_abc");
    assertEquals("UNSUBSCRIBED", response.status());
    verify(repo, times(1)).deleteByUserId("usr_abc");
  }

  @Test
  void unsubscribeIsSafeWhenNothingIsStored() {
    useCase.unsubscribe("usr_no_subscription");
    verify(repo, times(1)).deleteByUserId("usr_no_subscription");
  }
}
