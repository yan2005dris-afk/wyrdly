package com.wyrdly.post.application.listener;

import com.wyrdly.notifications.application.port.PushDispatcherPort;
import com.wyrdly.notifications.domain.model.PushEvent;
import com.wyrdly.post.domain.event.PostReactionEvent;
import com.wyrdly.post.domain.model.ReactionType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * CDI observer that turns a {@link PostReactionEvent} into a Web Push notification delivered to the
 * post's author. The service is responsible for the upstream filter (no REMOVED, no self-reaction,
 * no missing post); this listener keeps a defensive guard for the rare case the event is fired from
 * somewhere else.
 */
@ApplicationScoped
public class PostReactionPushEventListener {

  static final String DEEP_LINK_PREFIX = "/posts/";
  static final String TITLE = "Reacción a tu publicación";
  static final String BODY = "Alguien reaccionó a tu publicación en Wyrdly";

  private final PushDispatcherPort dispatcher;

  @Inject
  public PostReactionPushEventListener(PushDispatcherPort dispatcher) {
    this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
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

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("postId", event.postId());
    data.put("reactorId", event.reactorId());
    data.put("reactionType", notificationType);

    PushEvent push =
        new PushEvent(
            event.postAuthorId(),
            notificationType,
            TITLE,
            BODY,
            DEEP_LINK_PREFIX + event.postId(),
            data);
    dispatcher.dispatch(push);
  }

  private static String mapType(ReactionType type) {
    return switch (type) {
      case LIKE -> "POST_LIKE";
      case LOVE -> "POST_LOVE";
      case CELEBRATE -> "POST_CELEBRATE";
    };
  }
}
