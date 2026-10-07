package com.wyrdly.user.application.listener;

import com.wyrdly.notifications.application.port.PushDispatcherPort;
import com.wyrdly.notifications.domain.model.Notification;
import com.wyrdly.notifications.domain.repository.NotificationRepository;
import com.wyrdly.user.domain.event.UserFollowRelationshipChangedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CDI observer that turns a domain follow event into two side effects:
 *
 * <ol>
 *   <li>Persists an in-app {@link Notification} so the recipient sees it in the bell-shaped popover
 *       when they are online.
 *   <li>Dispatches the corresponding {@code PushEvent} to the Web Push pipeline so they also get an
 *       OS-level notification if they have an active browser subscription.
 * </ol>
 *
 * <p>Both side effects are best-effort: errors in persistence do not block dispatch and vice versa,
 * so a failure in one transport does not silently lose the other.
 */
@ApplicationScoped
public class UserFollowPushEventListener {

  private static final Logger LOG = Logger.getLogger(UserFollowPushEventListener.class.getName());

  static final String NOTIFICATION_TYPE = "GRAPH_FOLLOW";
  static final String TITLE = "Nuevo seguidor";
  static final String BODY = "Alguien comenzó a seguirte en Wyrdly";
  static final String DEEP_LINK = "/feed";

  private final PushDispatcherPort dispatcher;
  private final NotificationRepository notificationRepository;

  @Inject
  public UserFollowPushEventListener(
      PushDispatcherPort dispatcher, NotificationRepository notificationRepository) {
    this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
    this.notificationRepository =
        Objects.requireNonNull(notificationRepository, "notificationRepository must not be null");
  }

  void on(@Observes UserFollowRelationshipChangedEvent event) {
    Objects.requireNonNull(event, "event must not be null");
    if (!event.followed()) {
      return;
    }
    if (event.followerId().equals(event.targetUserId())) {
      return;
    }

    Notification notification =
        new Notification(
            nextId(),
            event.targetUserId(),
            NOTIFICATION_TYPE,
            event.followerId(),
            TITLE,
            BODY,
            DEEP_LINK,
            null,
            false,
            Instant.now());
    try {
      notificationRepository.save(notification);
    } catch (RuntimeException persistError) {
      // Persistence failure should not block the push dispatch.
      LOG.log(
          Level.WARNING,
          "Failed to persist follow notification for " + event.targetUserId(),
          persistError);
    }

    com.wyrdly.notifications.domain.model.PushEvent push =
        new com.wyrdly.notifications.domain.model.PushEvent(
            event.targetUserId(),
            NOTIFICATION_TYPE,
            TITLE,
            BODY,
            DEEP_LINK,
            Map.of("followerId", event.followerId()));
    dispatcher.dispatch(push);
  }

  private static String nextId() {
    return "ntf_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }
}
