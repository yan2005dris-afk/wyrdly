package com.wyrdly.notifications.application.port;

import com.wyrdly.notifications.application.dto.NotificationDto;
import io.smallrye.mutiny.Multi;

/** Port for broadcasting real-time notifications to active in-app SSE streams. */
public interface NotificationBroadcasterPort {

  /**
   * Broadcasts a notification to any active streams listening for the target user.
   *
   * @param targetUserId recipient user ID
   * @param notification notification DTO to deliver
   */
  void broadcast(String targetUserId, NotificationDto notification);

  /**
   * Obtains a reactive stream of notifications for the given user.
   *
   * @param userId user ID subscribing to their personal stream
   * @return Multi stream of notifications
   */
  Multi<NotificationDto> subscribe(String userId);
}
