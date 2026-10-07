package com.wyrdly.user.application.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wyrdly.notifications.application.port.PushDispatcherPort;
import com.wyrdly.notifications.domain.repository.NotificationRepository;
import com.wyrdly.user.domain.event.UserFollowRelationshipChangedEvent;
import com.wyrdly.user.domain.repository.UserProfileRepository;
import com.wyrdly.user.domain.repository.UserProfileRepository.FollowerSummary;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserFollowPushEventListenerTest {

  private PushDispatcherPort dispatcher;
  private NotificationRepository notificationRepository;
  private UserProfileRepository userProfileRepository;
  private UserFollowPushEventListener listener;

  @BeforeEach
  void setUp() {
    dispatcher = mock(PushDispatcherPort.class);
    notificationRepository = mock(NotificationRepository.class);
    userProfileRepository = mock(UserProfileRepository.class);
    listener =
        new UserFollowPushEventListener(dispatcher, notificationRepository, userProfileRepository);
    when(userProfileRepository.findProfileSummariesByIds(anySet())).thenReturn(Map.of());
  }

  @Test
  void persistsNotificationAndDispatchesPushWithActorName() {
    String followerId = "usr_follower";
    String targetUserId = "usr_target";
    when(userProfileRepository.findProfileSummariesByIds(Set.of(followerId)))
        .thenReturn(
            Map.of(followerId, new FollowerSummary(followerId, "bob", "Yandris Tech", "", false)));
    var event = new UserFollowRelationshipChangedEvent(followerId, targetUserId, true);

    listener.on(event);

    verify(notificationRepository)
        .save(
            argThat(
                n -> {
                  assertEquals(targetUserId, n.recipientUserId());
                  assertEquals(followerId, n.actorId());
                  assertEquals("Yandris Tech comenzó a seguirte en Wyrdly", n.body());
                  assertNotNull(n.id());
                  assertFalse(n.isRead());
                  return true;
                }));
    verify(dispatcher)
        .dispatch(
            argThat(
                push -> {
                  assertEquals("Yandris Tech comenzó a seguirte en Wyrdly", push.body());
                  assertEquals(targetUserId, push.recipientUserId());
                  return true;
                }));
  }

  @Test
  void fallsBackToGenericBodyWhenActorNotFound() {
    String followerId = "usr_ghost";
    String targetUserId = "usr_target";
    when(userProfileRepository.findProfileSummariesByIds(Set.of(followerId))).thenReturn(Map.of());
    var event = new UserFollowRelationshipChangedEvent(followerId, targetUserId, true);

    listener.on(event);

    verify(notificationRepository)
        .save(argThat(n -> "Alguien comenzó a seguirte en Wyrdly".equals(n.body())));
  }

  @Test
  void fallsBackToGenericBodyWhenActorHasNoFullName() {
    String followerId = "usr_anon";
    String targetUserId = "usr_target";
    when(userProfileRepository.findProfileSummariesByIds(Set.of(followerId)))
        .thenReturn(Map.of(followerId, new FollowerSummary(followerId, "anon", "", "", false)));
    var event = new UserFollowRelationshipChangedEvent(followerId, targetUserId, true);

    listener.on(event);

    verify(notificationRepository)
        .save(argThat(n -> "Alguien comenzó a seguirte en Wyrdly".equals(n.body())));
  }

  @Test
  void stillDispatchesPushWhenPersistenceFails() {
    var event = new UserFollowRelationshipChangedEvent("usr_a", "usr_b", true);
    when(userProfileRepository.findProfileSummariesByIds(anySet()))
        .thenReturn(Map.of("usr_a", new FollowerSummary("usr_a", "a", "Alice", "", false)));
    org.mockito.Mockito.doThrow(new RuntimeException("neo4j down"))
        .when(notificationRepository)
        .save(org.mockito.ArgumentMatchers.any());

    listener.on(event);

    verify(dispatcher).dispatch(argThat(push -> "usr_b".equals(push.recipientUserId())));
  }

  @Test
  void stillDispatchesPushWhenActorLookupFails() {
    String followerId = "usr_a";
    String targetUserId = "usr_b";
    when(userProfileRepository.findProfileSummariesByIds(anySet()))
        .thenThrow(new RuntimeException("neo4j down"));
    var event = new UserFollowRelationshipChangedEvent(followerId, targetUserId, true);

    listener.on(event);

    // Persistence still happens with the fallback body.
    verify(notificationRepository)
        .save(argThat(n -> "Alguien comenzó a seguirte en Wyrdly".equals(n.body())));
    verify(dispatcher)
        .dispatch(argThat(push -> "Alguien comenzó a seguirte en Wyrdly".equals(push.body())));
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
