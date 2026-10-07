package com.wyrdly.user.application.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.wyrdly.notifications.application.port.PushDispatcherPort;
import com.wyrdly.user.domain.event.UserFollowRelationshipChangedEvent;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserFollowPushEventListenerTest {

  private PushDispatcherPort dispatcher;
  private UserFollowPushEventListener listener;

  @BeforeEach
  void setUp() {
    dispatcher = mock(PushDispatcherPort.class);
    listener = new UserFollowPushEventListener(dispatcher);
  }

  @Test
  void dispatchesPushToTargetUserWhenFollowedIsTrue() {
    String followerId = "usr_follower";
    String targetUserId = "usr_target";
    var event = new UserFollowRelationshipChangedEvent(followerId, targetUserId, true);

    listener.on(event);

    verify(dispatcher)
        .dispatch(
            argThat(
                push -> {
                  assertNotNull(push);
                  assertEquals(targetUserId, push.recipientUserId());
                  assertEquals("GRAPH_FOLLOW", push.type());
                  assertEquals("Nuevo seguidor", push.title());
                  assertNotNull(push.body());
                  assertEquals("/feed", push.deepLink());
                  Map<String, Object> data = push.data();
                  assertEquals(followerId, data.get("followerId"));
                  return true;
                }));
  }

  @Test
  void doesNotDispatchWhenFollowedIsFalse() {
    var event = new UserFollowRelationshipChangedEvent("usr_a", "usr_b", false);

    listener.on(event);

    verifyNoInteractions(dispatcher);
  }

  @Test
  void doesNotDispatchOnSelfFollowEvenIfFollowedFlagIsTrue() {
    var event = new UserFollowRelationshipChangedEvent("usr_same", "usr_same", true);

    listener.on(event);

    verifyNoInteractions(dispatcher);
  }
}
