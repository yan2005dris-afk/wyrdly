package com.wyrdly.notifications.application.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wyrdly.notifications.application.port.NotificationBroadcasterPort;
import com.wyrdly.notifications.application.port.PushDispatcherPort;
import com.wyrdly.notifications.domain.repository.NotificationRepository;
import com.wyrdly.post.domain.event.PostReactionEvent;
import com.wyrdly.post.domain.model.ReactionType;
import com.wyrdly.user.domain.repository.UserProfileRepository;
import com.wyrdly.user.domain.repository.UserProfileRepository.FollowerSummary;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PostReactionNotificationEventListenerTest {

  private PushDispatcherPort dispatcher;
  private NotificationRepository notificationRepository;
  private UserProfileRepository userProfileRepository;
  private NotificationBroadcasterPort broadcaster;
  private PostReactionNotificationEventListener listener;

  @BeforeEach
  void setUp() {
    dispatcher = mock(PushDispatcherPort.class);
    notificationRepository = mock(NotificationRepository.class);
    userProfileRepository = mock(UserProfileRepository.class);
    broadcaster = mock(NotificationBroadcasterPort.class);
    listener =
        new PostReactionNotificationEventListener(
            dispatcher, notificationRepository, userProfileRepository, broadcaster);
    when(userProfileRepository.findProfileSummariesByIds(anySet())).thenReturn(Map.of());
  }

  @Test
  void persistsNotificationAndDispatchesPushOnLikeWithActorName() {
    var event = new PostReactionEvent("pst_1", "usr_author", "usr_reactor", ReactionType.LIKE);
    when(userProfileRepository.findProfileSummariesByIds(Set.of("usr_reactor")))
        .thenReturn(
            Map.of(
                "usr_reactor",
                new FollowerSummary("usr_reactor", "yandris", "Yandris Tech", "", false)));

    listener.on(event);

    verify(notificationRepository)
        .save(
            argThat(
                n -> {
                  assertEquals("usr_author", n.recipientUserId());
                  assertEquals("POST_LIKE", n.type());
                  assertEquals("usr_reactor", n.actorId());
                  assertEquals("Yandris Tech le dio Like a tu publicación", n.body());
                  assertEquals("/posts/pst_1", n.deepLink());
                  assertEquals("pst_1", n.targetResourceId());
                  assertNotNull(n.id());
                  return true;
                }));
    verify(dispatcher)
        .dispatch(
            argThat(
                push -> {
                  assertEquals("Yandris Tech le dio Like a tu publicación", push.body());
                  assertEquals("POST_LIKE", push.type());
                  return true;
                }));
    verify(broadcaster)
        .broadcast(
            org.mockito.ArgumentMatchers.eq("usr_author"),
            argThat(
                dto -> {
                  assertEquals("POST_LIKE", dto.type());
                  assertEquals("Yandris Tech le dio Like a tu publicación", dto.body());
                  assertEquals("Yandris Tech", dto.actor().fullName());
                  return true;
                }));
  }

  @Test
  void persistsLoveReactionWithActorName() {
    var event = new PostReactionEvent("pst_2", "usr_author", "usr_reactor", ReactionType.LOVE);
    when(userProfileRepository.findProfileSummariesByIds(Set.of("usr_reactor")))
        .thenReturn(
            Map.of(
                "usr_reactor", new FollowerSummary("usr_reactor", "r", "Yandris Tech", "", false)));

    listener.on(event);

    verify(notificationRepository)
        .save(argThat(n -> "Yandris Tech le dio Love a tu publicación".equals(n.body())));
    verify(dispatcher)
        .dispatch(argThat(push -> "Yandris Tech le dio Love a tu publicación".equals(push.body())));
  }

  @Test
  void persistsCelebrateReactionWithActorName() {
    var event = new PostReactionEvent("pst_3", "usr_author", "usr_reactor", ReactionType.CELEBRATE);
    when(userProfileRepository.findProfileSummariesByIds(Set.of("usr_reactor")))
        .thenReturn(
            Map.of(
                "usr_reactor", new FollowerSummary("usr_reactor", "r", "Yandris Tech", "", false)));

    listener.on(event);

    verify(notificationRepository)
        .save(argThat(n -> "Yandris Tech está celebrando tu publicación".equals(n.body())));
  }

  @Test
  void fallsBackToGenericBodyWhenActorNotFound() {
    var event = new PostReactionEvent("pst_1", "usr_author", "usr_actor", ReactionType.LIKE);
    when(userProfileRepository.findProfileSummariesByIds(Set.of("usr_actor"))).thenReturn(Map.of());

    listener.on(event);

    verify(notificationRepository)
        .save(argThat(n -> "Alguien le dio Like a tu publicación".equals(n.body())));
    verify(dispatcher)
        .dispatch(argThat(push -> "Alguien le dio Like a tu publicación".equals(push.body())));
  }

  @Test
  void stillDispatchesPushWhenPersistenceFails() {
    var event = new PostReactionEvent("pst_1", "usr_author", "usr_reactor", ReactionType.LIKE);
    when(userProfileRepository.findProfileSummariesByIds(anySet()))
        .thenReturn(
            Map.of("usr_reactor", new FollowerSummary("usr_reactor", "r", "Alice", "", false)));
    org.mockito.Mockito.doThrow(new RuntimeException("neo4j down"))
        .when(notificationRepository)
        .save(org.mockito.ArgumentMatchers.any());

    listener.on(event);

    verify(dispatcher).dispatch(argThat(push -> "usr_author".equals(push.recipientUserId())));
  }

  @Test
  void stillDispatchesPushWhenActorLookupFails() {
    var event = new PostReactionEvent("pst_1", "usr_author", "usr_reactor", ReactionType.LIKE);
    when(userProfileRepository.findProfileSummariesByIds(anySet()))
        .thenThrow(new RuntimeException("neo4j down"));

    listener.on(event);

    // Falls back to "Alguien" for both the persisted notification and the push.
    verify(notificationRepository)
        .save(argThat(n -> "Alguien le dio Like a tu publicación".equals(n.body())));
    verify(dispatcher)
        .dispatch(argThat(push -> "Alguien le dio Like a tu publicación".equals(push.body())));
  }

  @Test
  void doesNotPersistOrDispatchOnSelfReactionDefensively() {
    var event = new PostReactionEvent("pst_1", "usr_same", "usr_same", ReactionType.LIKE);

    listener.on(event);

    verifyNoInteractions(dispatcher);
    verifyNoInteractions(notificationRepository);
  }

  @Test
  void doesNotPersistOrDispatchWhenReactionTypeIsNull() {
    var event = new PostReactionEvent("pst_1", "usr_author", "usr_reactor", null);

    listener.on(event);

    verifyNoInteractions(dispatcher);
    verifyNoInteractions(notificationRepository);
  }
}
