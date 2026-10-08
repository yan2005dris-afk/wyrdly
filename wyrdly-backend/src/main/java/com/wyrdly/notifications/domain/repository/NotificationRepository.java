package com.wyrdly.notifications.domain.repository;

import com.wyrdly.notifications.domain.model.Notification;
import java.util.List;

/**
 * Port for persisting and reading {@link Notification}s. The in-app feed is independent of the Web
 * Push pipeline: the {@code PushDispatcher} may or may not find an active browser subscription for
 * the recipient, but every domain event that fires a notification must call {@link
 * #save(Notification)} regardless so the recipient sees it in the in-app popover when they are
 * online.
 */
public interface NotificationRepository {

  /** Persists a new notification. The {@link Notification#id()} must be unique. */
  void save(Notification notification);

  /**
   * Returns the recipient's notifications, newest first, capped at {@code limit} entries. Used by
   * {@code GET /api/notifications}.
   */
  List<Notification> findByRecipient(String recipientUserId, int page, int pageSize);

  /**
   * Returns the number of notifications for {@code recipientUserId} with {@code isRead = false}.
   * Drives the bell badge counter.
   */
  long countUnread(String recipientUserId);

  /**
   * Marks a single notification as read. No-op if the id does not exist or is not owned by {@code
   * userId}.
   */
  void markRead(String notificationId, String userId);

  /** Marks every unread notification for {@code userId} as read. Returns the count updated. */
  long markAllRead(String userId);
}
