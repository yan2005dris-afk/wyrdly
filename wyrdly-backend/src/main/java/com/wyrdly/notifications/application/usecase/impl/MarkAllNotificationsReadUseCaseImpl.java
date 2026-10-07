package com.wyrdly.notifications.application.usecase.impl;

import com.wyrdly.notifications.application.usecase.MarkAllNotificationsReadUseCase;
import com.wyrdly.notifications.domain.repository.NotificationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Objects;

@ApplicationScoped
public class MarkAllNotificationsReadUseCaseImpl implements MarkAllNotificationsReadUseCase {

  private final NotificationRepository repository;

  @Inject
  public MarkAllNotificationsReadUseCaseImpl(NotificationRepository repository) {
    this.repository = Objects.requireNonNull(repository, "repository");
  }

  @Override
  public long execute(String userId) {
    return repository.markAllRead(userId);
  }
}
