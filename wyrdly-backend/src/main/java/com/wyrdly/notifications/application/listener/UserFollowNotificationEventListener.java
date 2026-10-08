package com.wyrdly.notifications.application.listener;

import com.wyrdly.notifications.application.port.PushDispatcherPort;
import com.wyrdly.notifications.domain.model.Notification;
import com.wyrdly.notifications.domain.model.PushEvent;
import com.wyrdly.notifications.domain.repository.NotificationRepository;
import com.wyrdly.user.domain.event.UserFollowRelationshipChangedEvent;
import com.wyrdly.user.domain.repository.UserProfileRepository;
import com.wyrdly.user.domain.repository.UserProfileRepository.FollowerSummary;
import com.wyrdly.user.infrastructure.qualifier.ResilientNeo4j;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Notifications bounded context listener that observes {@link UserFollowRelationshipChangedEvent}
 * emitted by the user module. Decouples follow domain operations from notification persistence and
 * push dispatch.
 */
@ApplicationScoped
public class UserFollowNotificationEventListener {

  private static final Logger LOG =
      Logger.getLogger(UserFollowNotificationEventListener.class.getName());

  static final String NOTIFICATION_TYPE = "GRAPH_FOLLOW";
  static final String TITLE = "Nuevo seguidor";
  static final String BODY_FALLBACK = "Alguien comenzó a seguirte en Wyrdly";
  static final String DEEP_LINK = "/feed";

  private final PushDispatcherPort dispatcher;
  private final NotificationRepository notificationRepository;
  private final UserProfileRepository userProfileRepository;

  @Inject
  public UserFollowNotificationEventListener(
      PushDispatcherPort dispatcher,
      NotificationRepository notificationRepository,
      @ResilientNeo4j UserProfileRepository userProfileRepository) {
    this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
    this.notificationRepository =
        Objects.requireNonNull(notificationRepository, "notificationRepository must not be null");
    this.userProfileRepository =
        Objects.requireNonNull(userProfileRepository, "userProfileRepository must not be null");
  }

  public void on(@Observes UserFollowRelationshipChangedEvent event) {
    Objects.requireNonNull(event, "event must not be null");
    if (!event.followed()) {
      return;
    }
    if (event.followerId().equals(event.targetUserId())) {
      return;
    }

    String actorName = resolveActorName(event.followerId());
    String body = buildBody(actorName);

    Notification notification =
        new Notification(
            nextId(),
            event.targetUserId(),
            NOTIFICATION_TYPE,
            event.followerId(),
            TITLE,
            body,
            DEEP_LINK,
            null,
            false,
            Instant.now());
    try {
      notificationRepository.save(notification);
    } catch (RuntimeException persistError) {
      LOG.log(
          Level.WARNING,
          "Failed to persist follow notification for " + event.targetUserId(),
          persistError);
    }

    PushEvent push =
        new PushEvent(
            event.targetUserId(),
            NOTIFICATION_TYPE,
            TITLE,
            body,
            DEEP_LINK,
            Map.of("followerId", event.followerId()));
    dispatcher.dispatch(push);
  }

  private String resolveActorName(String actorId) {
    try {
      var actors = userProfileRepository.findProfileSummariesByIds(Set.of(actorId));
      FollowerSummary actor = actors.get(actorId);
      if (actor == null || actor.fullName() == null || actor.fullName().isBlank()) {
        return null;
      }
      return actor.fullName();
    } catch (RuntimeException lookupError) {
      LOG.log(
          Level.WARNING, "Failed to resolve follower's display name for " + actorId, lookupError);
      return null;
    }
  }

  private static String buildBody(String actorName) {
    return actorName == null ? BODY_FALLBACK : actorName + " comenzó a seguirte en Wyrdly";
  }

  private static String nextId() {
    return "ntf_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }
}
