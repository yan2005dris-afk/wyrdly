package com.wyrdly.notifications.application.usecase.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wyrdly.notifications.domain.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MarkAllNotificationsReadUseCaseImplTest {

  private NotificationRepository repository;
  private MarkAllNotificationsReadUseCaseImpl useCase;

  @BeforeEach
  void setUp() {
    repository = mock(NotificationRepository.class);
    useCase = new MarkAllNotificationsReadUseCaseImpl(repository);
  }

  @Test
  void returnsCountFromRepository() {
    when(repository.markAllRead("usr_alice")).thenReturn(7L);

    long updated = useCase.execute("usr_alice");

    assertEquals(7L, updated);
  }

  @Test
  void returnsZeroWhenRepositoryReportsNoChanges() {
    when(repository.markAllRead("usr_bob")).thenReturn(0L);

    assertEquals(0L, useCase.execute("usr_bob"));
  }
}
