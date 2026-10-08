package com.wyrdly.notifications.application.listener;

import com.wyrdly.notifications.application.dto.NotificationDto;
import com.wyrdly.notifications.application.port.NotificationBroadcasterPort;
import com.wyrdly.notifications.application.port.PushDispatcherPort;
import com.wyrdly.notifications.domain.model.Notification;
import com.wyrdly.notifications.domain.model.PushEvent;
import com.wyrdly.notifications.domain.repository.NotificationRepository;
import com.wyrdly.post.domain.event.CommentCreatedEvent;
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
 * Notifications bounded context listener that observes {@link CommentCreatedEvent} emitted by
 * {@code CommentService}. Decouples comment persistence from notification persistence and push
 * dispatch.
 *
 * <p>Self-comments (where {@code commentAuthorId == postAuthorId}) are skipped so that users do not
 * get notifications for their own activity. The in-app broadcast and Web Push delivery are both
 * best-effort: a downstream failure logs a warning but never propagates back into the comment write
 * path.
 */
@ApplicationScoped
public class CommentNotificationEventListener {

  private static final Logger LOG =
      Logger.getLogger(CommentNotificationEventListener.class.getName());

  static final String NOTIFICATION_TYPE = "POST_COMMENT";
  static final String TITLE = "Nuevo comentario en tu publicación";
  static final String DEEP_LINK_PREFIX = "/feed#post-";
  static final int MAX_SNIPPET_LENGTH = 140;

  private final PushDispatcherPort dispatcher;
  private final NotificationRepository notificationRepository;
  private final UserProfileRepository userProfileRepository;
  private final NotificationBroadcasterPort broadcaster;

  @Inject
  public CommentNotificationEventListener(
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

  /**
   * Persists an in-app notification, broadcasts over SSE for active tabs, and dispatches a Web Push
   * payload. Skips self-comments to avoid notifying users of their own activity. Each side effect
   * is wrapped in its own try/catch so a transient failure in one channel does not abort the
   * others.
   */
  public void on(@Observes CommentCreatedEvent event) {
    Objects.requireNonNull(event, "event must not be null");

    if (event.commentAuthorId().equals(event.postAuthorId())) {
      return;
    }

    FollowerSummary authorSummary = resolveActorSummary(event.commentAuthorId());
    String authorName =
        (authorSummary != null
                && authorSummary.fullName() != null
                && !authorSummary.fullName().isBlank())
            ? authorSummary.fullName()
            : null;

    int limit = Math.min(event.content().length(), MAX_SNIPPET_LENGTH);
    String snippet = event.content().substring(0, limit);
    String body =
        authorName != null
            ? authorName + " comentó: \"" + snippet + "\""
            : "Nuevo comentario: \"" + snippet + "\"";
    String deepLink = DEEP_LINK_PREFIX + event.postId();

    Notification notification =
        new Notification(
            nextId(),
            event.postAuthorId(),
            NOTIFICATION_TYPE,
            event.commentAuthorId(),
            TITLE,
            body,
            deepLink,
            event.postId(),
            false,
            Instant.now());

    try {
      notificationRepository.save(notification);
    } catch (RuntimeException persistError) {
      LOG.log(Level.WARNING, "Failed to persist comment notification", persistError);
    }

    NotificationDto.ActorDto actorDto = buildActorDto(event.commentAuthorId(), authorSummary);
    NotificationDto ssePayload = NotificationDto.from(notification, actorDto);
    try {
      broadcaster.broadcast(event.postAuthorId(), ssePayload);
    } catch (RuntimeException sseError) {
      LOG.log(Level.FINE, "Failed to broadcast comment notification to SSE", sseError);
    }

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("postId", event.postId());
    data.put("commentId", event.commentId());
    data.put("commentAuthorId", event.commentAuthorId());

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
      LOG.log(Level.WARNING, "Failed to resolve comment author name for " + actorId, lookupError);
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
