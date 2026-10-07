package com.wyrdly.user.application.listener;

import com.wyrdly.notifications.application.port.PushDispatcherPort;
import com.wyrdly.notifications.domain.model.PushEvent;
import com.wyrdly.user.domain.event.UserFollowRelationshipChangedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.Objects;

/**
 * CDI observer that turns a domain follow event into a Web Push notification delivered to the user
 * being followed. The unfollow case is intentionally a no-op (only new followers warrant a push); a
 * defensive self-follow guard is also in place even though {@code FollowUserUseCaseImpl} already
 * rejects that path.
 *
 * <p>The notification payload is intentionally minimal (generic title, deep link to {@code /feed}).
 * Enriching the body with the follower's display name requires a username lookup on the graph,
 * which is out of scope for this wiring PR.
 */
@ApplicationScoped
public class UserFollowPushEventListener {

  static final String NOTIFICATION_TYPE = "GRAPH_FOLLOW";
  static final String TITLE = "Nuevo seguidor";
  static final String BODY = "Alguien comenzó a seguirte en Wyrdly";
  static final String DEEP_LINK = "/feed";

  private final PushDispatcherPort dispatcher;

  @Inject
  public UserFollowPushEventListener(PushDispatcherPort dispatcher) {
    this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
  }

  void on(@Observes UserFollowRelationshipChangedEvent event) {
    Objects.requireNonNull(event, "event must not be null");
    if (!event.followed()) {
      return;
    }
    if (event.followerId().equals(event.targetUserId())) {
      return;
    }
    PushEvent push =
        new PushEvent(
            event.targetUserId(),
            NOTIFICATION_TYPE,
            TITLE,
            BODY,
            DEEP_LINK,
            Map.of("followerId", event.followerId()));
    dispatcher.dispatch(push);
  }
}
