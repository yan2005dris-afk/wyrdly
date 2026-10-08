package com.wyrdly.notifications.application.usecase;

/** Marks a single notification as read. No-op if the id does not belong to {@code userId}. */
public interface MarkNotificationReadUseCase {

  void execute(String userId, String notificationId);
}
