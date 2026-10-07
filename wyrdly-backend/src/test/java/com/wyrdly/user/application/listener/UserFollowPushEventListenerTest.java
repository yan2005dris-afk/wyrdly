package com.wyrdly.user.application.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.wyrdly.notifications.application.port.PushDispatcherPort;
import com.wyrdly.notifications.domain.repository.NotificationRepository;
import com.wyrdly.user.domain.event.UserFollowRelationshipChangedEvent;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserFollowPushEventListenerTest {

  private PushDispatcherPort dispatcher;
  private NotificationRepository notificationRepository;
  private UserFollowPushEventListener listener;

  @BeforeEach
  void setUp() {
    dispatcher = mock(PushDispatcherPort.class);
    notificationRepository = mock(NotificationRepository.class);
    listener = new UserFollowPushEventListener(dispatcher, notificationRepository);
  }

  @Test
  void persistsNotificationAndDispatchesPushWhenFollowedIsTrue() {
    String followerId = "usr_follower";
    String targetUserId = "usr_target";
    var event = new UserFollowRelationshipChangedEvent(followerId, targetUserId, true);

    listener.on(event);

    verify(notificationRepository)
        .save(
            argThat(
                n -> {
                  assertEquals(targetUserId, n.recipientUserId());
                  assertEquals("GRAPH_FOLLOW", n.type());
                  assertEquals(followerId, n.actorId());
                  assertEquals("Nuevo seguidor", n.title());
                  assertEquals("/feed", n.deepLink());
                  assertNotNull(n.id());
                  assertNotNull(n.createdAt());
                  assertFalse(n.isRead());
                  return true;
                }));
    verify(dispatcher)
        .dispatch(
            argThat(
                push -> {
                  assertNotNull(push);
                  assertEquals(targetUserId, push.recipientUserId());
                  assertEquals("GRAPH_FOLLOW", push.type());
                  assertEquals("Nuevo seguidor", push.title());
                  assertEquals("/feed", push.deepLink());
                  Map<String, Object> data = push.data();
                  assertEquals(followerId, data.get("followerId"));
                  return true;
                }));
  }

  @Test
  void stillDispatchesPushWhenPersistenceFails() {
    var event = new UserFollowRelationshipChangedEvent("usr_a", "usr_b", true);
    org.mockito.Mockito.doThrow(new RuntimeException("neo4j down"))
        .when(notificationRepository)
        .save(org.mockito.ArgumentMatchers.any());

    listener.on(event);

    verify(dispatcher).dispatch(argThat(push -> "usr_b".equals(push.recipientUserId())));
  }

  @Test
  void doesNotPersistOrDispatchWhenFollowedIsFalse() {
    var event = new UserFollowRelationshipChangedEvent("usr_a", "usr_b", false);

    listener.on(event);

    verifyNoInteractions(dispatcher);
    verifyNoInteractions(notificationRepository);
  }

  @Test
  void doesNotPersistOrDispatchOnSelfFollowEvenIfFollowedFlagIsTrue() {
    var event = new UserFollowRelationshipChangedEvent("usr_same", "usr_same", true);

    listener.on(event);

    verifyNoInteractions(dispatcher);
    verifyNoInteractions(notificationRepository);
  }
}
