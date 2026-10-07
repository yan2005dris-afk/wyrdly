package com.wyrdly.notifications.application.usecase.impl;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.wyrdly.notifications.domain.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MarkNotificationReadUseCaseImplTest {

  private NotificationRepository repository;
  private MarkNotificationReadUseCaseImpl useCase;

  @BeforeEach
  void setUp() {
    repository = mock(NotificationRepository.class);
    useCase = new MarkNotificationReadUseCaseImpl(repository);
  }

  @Test
  void delegatesToRepositoryWithBothIds() {
    useCase.execute("usr_alice", "ntf_abc");

    verify(repository).markRead("ntf_abc", "usr_alice");
  }
}
