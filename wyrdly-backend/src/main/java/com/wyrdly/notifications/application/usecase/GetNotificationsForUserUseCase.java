package com.wyrdly.notifications.application.usecase;

import com.wyrdly.notifications.application.dto.NotificationListResponseDto;

/** Returns the recipient's notification feed (newest first) plus the unread count for the badge. */
public interface GetNotificationsForUserUseCase {

  NotificationListResponseDto execute(String userId, int page, int pageSize);
}
