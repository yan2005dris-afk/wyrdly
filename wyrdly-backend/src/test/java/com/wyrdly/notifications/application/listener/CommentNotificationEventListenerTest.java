package com.wyrdly.notifications.application.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wyrdly.notifications.application.port.NotificationBroadcasterPort;
import com.wyrdly.notifications.application.port.PushDispatcherPort;
import com.wyrdly.notifications.domain.repository.NotificationRepository;
import com.wyrdly.post.domain.event.CommentCreatedEvent;
import com.wyrdly.user.domain.repository.UserProfileRepository;
import com.wyrdly.user.domain.repository.UserProfileRepository.FollowerSummary;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link CommentNotificationEventListener}. */
class CommentNotificationEventListenerTest {

  private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

  private PushDispatcherPort dispatcher;
  private NotificationRepository notificationRepository;
  private UserProfileRepository userProfileRepository;
  private NotificationBroadcasterPort broadcaster;
  private CommentNotificationEventListener listener;

  @BeforeEach
  void setUp() {
    dispatcher = mock(PushDispatcherPort.class);
    notificationRepository = mock(NotificationRepository.class);
    userProfileRepository = mock(UserProfileRepository.class);
    broadcaster = mock(NotificationBroadcasterPort.class);
    listener =
        new CommentNotificationEventListener(
            dispatcher, notificationRepository, userProfileRepository, broadcaster);
    when(userProfileRepository.findProfileSummariesByIds(anySet())).thenReturn(Map.of());
  }

  private static CommentCreatedEvent event(String commentAuthorId, String postAuthorId) {
    return new CommentCreatedEvent(
        "cmt_1", "pst_1", postAuthorId, commentAuthorId, "Great post!", NOW);
  }

  @Test
  void dispatchesPushWhenAuthorIsDifferent() {
    when(userProfileRepository.findProfileSummariesByIds(Set.of("usr_alice")))
        .thenReturn(
            Map.of(
                "usr_alice",
                new FollowerSummary("usr_alice", "alice", "Alice", "https://a.jpg", false)));

    listener.on(event("usr_alice", "usr_bob"));

    verify(notificationRepository)
        .save(
            argThat(
                n -> {
                  assertEquals("usr_bob", n.recipientUserId());
                  assertEquals("POST_COMMENT", n.type());
                  assertEquals("usr_alice", n.actorId());
                  assertEquals("Alice comentó: \"Great post!\"", n.body());
                  assertEquals("/feed#post-pst_1", n.deepLink());
                  assertEquals("pst_1", n.targetResourceId());
                  assertNotNull(n.id());
                  return true;
                }));
    verify(broadcaster)
        .broadcast(
            org.mockito.ArgumentMatchers.eq("usr_bob"),
            argThat(
                dto -> {
                  assertEquals("POST_COMMENT", dto.type());
                  assertEquals("Alice", dto.actor().fullName());
                  assertEquals("Alice comentó: \"Great post!\"", dto.body());
                  return true;
                }));
    verify(dispatcher)
        .dispatch(
            argThat(
                push -> {
                  assertEquals("usr_bob", push.recipientUserId());
                  assertEquals("POST_COMMENT", push.type());
                  assertEquals("Alice comentó: \"Great post!\"", push.body());
                  assertEquals("pst_1", push.data().get("postId"));
                  assertEquals("cmt_1", push.data().get("commentId"));
                  assertEquals("usr_alice", push.data().get("commentAuthorId"));
                  return true;
                }));
  }

  @Test
  void doesNotDispatchPushForSelfComment() {
    listener.on(event("usr_alice", "usr_alice"));

    verifyNoInteractions(dispatcher);
    verifyNoInteractions(notificationRepository);
    verifyNoInteractions(broadcaster);
  }

  @Test
  void usesFallbackBodyWhenAuthorProfileMissing() {
    when(userProfileRepository.findProfileSummariesByIds(Set.of("usr_alice"))).thenReturn(Map.of());

    listener.on(event("usr_alice", "usr_bob"));

    verify(notificationRepository)
        .save(argThat(n -> "Nuevo comentario: \"Great post!\"".equals(n.body())));
    verify(dispatcher)
        .dispatch(argThat(push -> "Nuevo comentario: \"Great post!\"".equals(push.body())));
  }

  @Test
  void continuesDispatchingWhenPersistThrows() {
    when(userProfileRepository.findProfileSummariesByIds(anySet()))
        .thenReturn(
            Map.of(
                "usr_alice",
                new FollowerSummary("usr_alice", "alice", "Alice", "https://a.jpg", false)));
    doThrow(new RuntimeException("neo4j down"))
        .when(notificationRepository)
        .save(org.mockito.ArgumentMatchers.any());

    listener.on(event("usr_alice", "usr_bob"));

    // Despite persistence failure, push and SSE still fire.
    verify(dispatcher).dispatch(argThat(push -> "usr_bob".equals(push.recipientUserId())));
    verify(broadcaster)
        .broadcast(org.mockito.ArgumentMatchers.eq("usr_bob"), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void continuesDispatchingWhenBroadcastThrows() {
    when(userProfileRepository.findProfileSummariesByIds(anySet()))
        .thenReturn(
            Map.of(
                "usr_alice",
                new FollowerSummary("usr_alice", "alice", "Alice", "https://a.jpg", false)));
    doThrow(new RuntimeException("sse down"))
        .when(broadcaster)
        .broadcast(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());

    listener.on(event("usr_alice", "usr_bob"));

    // Persistence succeeds, push still dispatches, SSE failure is swallowed.
    verify(notificationRepository).save(argThat(n -> "POST_COMMENT".equals(n.type())));
    verify(dispatcher).dispatch(argThat(push -> "usr_bob".equals(push.recipientUserId())));
  }

  @Test
  void truncatesSnippetTo140Chars() {
    StringBuilder longContent = new StringBuilder();
    for (int i = 0; i < 250; i++) {
      longContent.append('x');
    }
    CommentCreatedEvent longEvent =
        new CommentCreatedEvent(
            "cmt_1", "pst_1", "usr_bob", "usr_alice", longContent.toString(), NOW);

    listener.on(longEvent);

    verify(notificationRepository)
        .save(
            argThat(
                n -> {
                  // body contains the truncated 140-char snippet
                  String expectedSnippet = "x".repeat(140);
                  assertEquals("Nuevo comentario: \"" + expectedSnippet + "\"", n.body());
                  return true;
                }));
  }
}
