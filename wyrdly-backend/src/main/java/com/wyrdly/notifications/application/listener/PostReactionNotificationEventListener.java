package com.wyrdly.notifications.application.listener;

import com.wyrdly.notifications.application.port.PushDispatcherPort;
import com.wyrdly.notifications.domain.model.Notification;
import com.wyrdly.notifications.domain.model.PushEvent;
import com.wyrdly.notifications.domain.repository.NotificationRepository;
import com.wyrdly.post.domain.event.PostReactionEvent;
import com.wyrdly.post.domain.model.ReactionType;
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
 * Notifications bounded context listener that observes {@link PostReactionEvent} emitted by the
 * post module. Decouples post reaction operations from notification persistence and push dispatch.
 */
@ApplicationScoped
public class PostReactionNotificationEventListener {

  private static final Logger LOG =
      Logger.getLogger(PostReactionNotificationEventListener.class.getName());

  static final String DEEP_LINK_PREFIX = "/posts/";
  static final String TITLE = "Reacción a tu publicación";
  static final String BODY_FALLBACK_LIKE = "Alguien le dio Like a tu publicación";
  static final String BODY_FALLBACK_LOVE = "Alguien le dio Love a tu publicación";
  static final String BODY_FALLBACK_CELEBRATE = "Alguien está celebrando tu publicación";
  static final String BODY_LIKE_SUFFIX = " le dio Like a tu publicación";
  static final String BODY_LOVE_SUFFIX = " le dio Love a tu publicación";
  static final String BODY_CELEBRATE_SUFFIX = " está celebrando tu publicación";

  private final PushDispatcherPort dispatcher;
  private final NotificationRepository notificationRepository;
  private final UserProfileRepository userProfileRepository;

  @Inject
  public PostReactionNotificationEventListener(
      PushDispatcherPort dispatcher,
      NotificationRepository notificationRepository,
      @ResilientNeo4j UserProfileRepository userProfileRepository) {
    this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
    this.notificationRepository =
        Objects.requireNonNull(notificationRepository, "notificationRepository must not be null");
    this.userProfileRepository =
        Objects.requireNonNull(userProfileRepository, "userProfileRepository must not be null");
  }

  public void on(@Observes PostReactionEvent event) {
    Objects.requireNonNull(event, "event must not be null");
    if (event.reactionType() == null) {
      return;
    }
    if (event.reactorId() != null && event.reactorId().equals(event.postAuthorId())) {
      return;
    }
    String notificationType = mapType(event.reactionType());
    String deepLink = DEEP_LINK_PREFIX + event.postId();

    String actorName = resolveActorName(event.reactorId());
    String body = buildBody(actorName, event.reactionType());

    Notification notification =
        new Notification(
            nextId(),
            event.postAuthorId(),
            notificationType,
            event.reactorId(),
            TITLE,
            body,
            deepLink,
            event.postId(),
            false,
            Instant.now());
    try {
      notificationRepository.save(notification);
    } catch (RuntimeException persistError) {
      LOG.log(
          Level.WARNING,
          "Failed to persist reaction notification for " + event.postAuthorId(),
          persistError);
    }

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("postId", event.postId());
    data.put("reactorId", event.reactorId());
    data.put("reactionType", notificationType);

    PushEvent push =
        new PushEvent(event.postAuthorId(), notificationType, TITLE, body, deepLink, data);
    dispatcher.dispatch(push);
  }

  private String resolveActorName(String actorId) {
    if (actorId == null) {
      return null;
    }
    try {
      var actors = userProfileRepository.findProfileSummariesByIds(Set.of(actorId));
      FollowerSummary actor = actors.get(actorId);
      if (actor == null || actor.fullName() == null || actor.fullName().isBlank()) {
        return null;
      }
      return actor.fullName();
    } catch (RuntimeException lookupError) {
      LOG.log(
          Level.WARNING, "Failed to resolve reactor's display name for " + actorId, lookupError);
      return null;
    }
  }

  private static String buildBody(String actorName, ReactionType type) {
    if (actorName == null) {
      return switch (type) {
        case LIKE -> BODY_FALLBACK_LIKE;
        case LOVE -> BODY_FALLBACK_LOVE;
        case CELEBRATE -> BODY_FALLBACK_CELEBRATE;
      };
    }
    return actorName
        + switch (type) {
          case LIKE -> BODY_LIKE_SUFFIX;
          case LOVE -> BODY_LOVE_SUFFIX;
          case CELEBRATE -> BODY_CELEBRATE_SUFFIX;
        };
  }

  private static String mapType(ReactionType type) {
    return switch (type) {
      case LIKE -> "POST_LIKE";
      case LOVE -> "POST_LOVE";
      case CELEBRATE -> "POST_CELEBRATE";
    };
  }

  private static String nextId() {
    return "ntf_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }
}
