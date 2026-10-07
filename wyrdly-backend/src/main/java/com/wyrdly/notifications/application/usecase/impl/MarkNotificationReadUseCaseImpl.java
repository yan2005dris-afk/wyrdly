package com.wyrdly.notifications.application.usecase.impl;

import com.wyrdly.notifications.application.usecase.MarkNotificationReadUseCase;
import com.wyrdly.notifications.domain.repository.NotificationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Objects;

@ApplicationScoped
public class MarkNotificationReadUseCaseImpl implements MarkNotificationReadUseCase {

  private final NotificationRepository repository;

  @Inject
  public MarkNotificationReadUseCaseImpl(NotificationRepository repository) {
    this.repository = Objects.requireNonNull(repository, "repository");
  }

  @Override
  public void execute(String userId, String notificationId) {
    repository.markRead(notificationId, userId);
  }
}
