package com.wyrdly.notifications.application.usecase;

/** Marks every unread notification for {@code userId} as read. Returns the count updated. */
public interface MarkAllNotificationsReadUseCase {

  long execute(String userId);
}
