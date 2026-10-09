package com.wyrdly.notifications.application.listener;

import com.wyrdly.notifications.application.dto.NotificationDto;
import com.wyrdly.notifications.application.port.NotificationBroadcasterPort;
import com.wyrdly.notifications.application.port.PushDispatcherPort;
import com.wyrdly.notifications.domain.model.Notification;
import com.wyrdly.notifications.domain.model.PushEvent;
import com.wyrdly.notifications.domain.repository.NotificationRepository;
import com.wyrdly.post.domain.event.PostRepostedEvent;
import com.wyrdly.user.domain.repository.UserProfileRepository;
import com.wyrdly.user.domain.repository.UserProfileRepository.FollowerSummary;
import com.wyrdly.user.infrastructure.qualifier.ResilientNeo4j;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Notifications bounded context listener that observes {@link PostRepostedEvent} emitted by {@code
 * RepostService}. Decouples repost persistence from notification persistence and push dispatch.
 */
@ApplicationScoped
public class RepostNotificationEventListener {

  private static final Logger LOG =
      Logger.getLogger(RepostNotificationEventListener.class.getName());

  static final String NOTIFICATION_TYPE = "POST_BOOST";
  static final String TITLE = "Nueva republicación";
  static final String DEEP_LINK_PREFIX = "/feed#post-";

  private final PushDispatcherPort dispatcher;
  private final NotificationRepository notificationRepository;
  private final UserProfileRepository userProfileRepository;
  private final NotificationBroadcasterPort broadcaster;

  @Inject
  public RepostNotificationEventListener(
      PushDispatcherPort dispatcher,
      NotificationRepository notificationRepository,
      @ResilientNeo4j UserProfileRepository userProfileRepository,
      NotificationBroadcasterPort broadcaster) {
    this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
    this.notificationRepository =
        Objects.requireNonNull(notificationRepository, "notificationRepository must not be null");
    this.userProfileRepository =
        Objects.requireNonNull(userProfileRepository, "userProfileRepository must not be null");
    this.broadcaster = Objects.requireNonNull(broadcaster, "broadcaster must not be null");
  }

  public void on(@Observes PostRepostedEvent event) {
    Objects.requireNonNull(event, "event must not be null");

    if (event.actorId().equals(event.postAuthorId())) {
      return;
    }

    FollowerSummary actorSummary = resolveActorSummary(event.actorId());
    String actorName =
        (actorSummary != null
                && actorSummary.fullName() != null
                && !actorSummary.fullName().isBlank())
            ? actorSummary.fullName()
            : null;

    String body =
        actorName != null
            ? actorName + " compartió tu publicación"
            : "Alguien compartió tu publicación";
    String deepLink = DEEP_LINK_PREFIX + event.postId();

    Notification notification =
        new Notification(
            nextId(),
            event.postAuthorId(),
            NOTIFICATION_TYPE,
            event.actorId(),
            TITLE,
            body,
            deepLink,
            event.postId(),
            false,
            Instant.now());

    try {
      notificationRepository.save(notification);
    } catch (RuntimeException persistError) {
      LOG.log(Level.WARNING, "Failed to persist repost notification", persistError);
    }

    NotificationDto.ActorDto actorDto = buildActorDto(event.actorId(), actorSummary);
    NotificationDto ssePayload = NotificationDto.from(notification, actorDto);
    try {
      broadcaster.broadcast(event.postAuthorId(), ssePayload);
    } catch (RuntimeException sseError) {
      LOG.log(Level.FINE, "Failed to broadcast repost notification to SSE", sseError);
    }

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("postId", event.postId());
    data.put("actorId", event.actorId());

    PushEvent push =
        new PushEvent(event.postAuthorId(), NOTIFICATION_TYPE, TITLE, body, deepLink, data);
    dispatcher.dispatch(push);
  }

  private FollowerSummary resolveActorSummary(String actorId) {
    if (actorId == null) {
      return null;
    }
    try {
      var actors = userProfileRepository.findProfileSummariesByIds(Set.of(actorId));
      return actors.get(actorId);
    } catch (RuntimeException lookupError) {
      LOG.log(Level.WARNING, "Failed to resolve actor summary for " + actorId, lookupError);
      return null;
    }
  }

  private static NotificationDto.ActorDto buildActorDto(
      String actorId, FollowerSummary actorSummary) {
    if (actorId == null) {
      return null;
    }
    if (actorSummary == null) {
      return NotificationDto.ActorDto.placeholder(actorId);
    }
    return new NotificationDto.ActorDto(
        actorSummary.id(),
        actorSummary.username(),
        actorSummary.fullName(),
        actorSummary.avatarUrl(),
        null);
  }

  private static String nextId() {
    return "ntf_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }
}
