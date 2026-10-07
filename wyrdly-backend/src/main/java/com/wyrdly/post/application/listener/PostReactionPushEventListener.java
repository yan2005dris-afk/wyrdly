package com.wyrdly.post.application.listener;

import com.wyrdly.notifications.application.port.PushDispatcherPort;
import com.wyrdly.notifications.domain.model.Notification;
import com.wyrdly.notifications.domain.model.PushEvent;
import com.wyrdly.notifications.domain.repository.NotificationRepository;
import com.wyrdly.post.domain.event.PostReactionEvent;
import com.wyrdly.post.domain.model.ReactionType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CDI observer that turns a {@link PostReactionEvent} into two side effects: a persisted {@link
 * Notification} for the in-app feed and a dispatched {@link PushEvent} for the OS-level push
 * pipeline. The service is responsible for the upstream filter (no REMOVED, no self-reaction, no
 * missing post); this listener keeps a defensive guard for the rare case the event is fired from
 * somewhere else.
 */
@ApplicationScoped
public class PostReactionPushEventListener {

  private static final Logger LOG = Logger.getLogger(PostReactionPushEventListener.class.getName());

  static final String DEEP_LINK_PREFIX = "/posts/";
  static final String TITLE = "Reacción a tu publicación";
  static final String BODY = "Alguien reaccionó a tu publicación en Wyrdly";

  private final PushDispatcherPort dispatcher;
  private final NotificationRepository notificationRepository;

  @Inject
  public PostReactionPushEventListener(
      PushDispatcherPort dispatcher, NotificationRepository notificationRepository) {
    this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
    this.notificationRepository =
        Objects.requireNonNull(notificationRepository, "notificationRepository must not be null");
  }

  void on(@Observes PostReactionEvent event) {
    Objects.requireNonNull(event, "event must not be null");
    if (event.reactionType() == null) {
      return;
    }
    if (event.reactorId() != null && event.reactorId().equals(event.postAuthorId())) {
      return;
    }
    String notificationType = mapType(event.reactionType());
    String deepLink = DEEP_LINK_PREFIX + event.postId();

    Notification notification =
        new Notification(
            nextId(),
            event.postAuthorId(),
            notificationType,
            event.reactorId(),
            TITLE,
            BODY,
            deepLink,
            event.postId(),
            false,
            Instant.now());
    try {
      notificationRepository.save(notification);
    } catch (RuntimeException persistError) {
      // Persistence failure should not block the push dispatch.
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
        new PushEvent(event.postAuthorId(), notificationType, TITLE, BODY, deepLink, data);
    dispatcher.dispatch(push);
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
