package com.wyrdly.post.application.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.wyrdly.notifications.application.usecase.NotifyFollowersUseCase;
import com.wyrdly.notifications.domain.model.PushMessage;
import com.wyrdly.post.domain.event.PostPublishedEvent;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PostPublishedPushEventListenerTest {

  private NotifyFollowersUseCase notifyFollowers;
  private PostPublishedPushEventListener listener;

  @BeforeEach
  void setUp() {
    notifyFollowers = mock(NotifyFollowersUseCase.class);
    listener = new PostPublishedPushEventListener(notifyFollowers);
  }

  @Test
  void fansOutNewPostMessageToAuthorFollowers() {
    listener.on(
        new PostPublishedEvent("pst_1", "usr_author", "alice", "Hola mundo", Instant.now()));

    ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
    verify(notifyFollowers).notifyFollowers(eq("usr_author"), captor.capture());
    PushMessage message = captor.getValue();
    assertEquals("NEW_POST_FROM_FOLLOWED", message.type());
    assertEquals("Nueva publicación de alice", message.title());
    assertEquals("Hola mundo", message.body());
    assertEquals("/posts/pst_1", message.deepLink());
    assertEquals("pst_1", message.data().get("postId"));
    assertEquals("usr_author", message.data().get("authorId"));
  }

  @Test
  void truncatesLongContentTo140CodePointsWithEllipsis() {
    String content = "a".repeat(500);
    listener.on(new PostPublishedEvent("pst_1", "usr_author", "alice", content, Instant.now()));

    ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
    verify(notifyFollowers).notifyFollowers(eq("usr_author"), captor.capture());
    String body = captor.getValue().body();
    assertEquals(140, body.codePointCount(0, body.length()));
    assertTrue(body.endsWith("…"));
  }

  @Test
  void snippetNeverSplitsSurrogatePairs() {
    String content = "😀".repeat(200);
    String snippet = PostPublishedPushEventListener.snippet(content);

    assertEquals(140, snippet.codePointCount(0, snippet.length()));
    assertEquals("😀".repeat(139) + "…", snippet);
  }

  @Test
  void snippetCollapsesWhitespaceAndKeepsShortContentIntact() {
    assertEquals("hola mundo", PostPublishedPushEventListener.snippet("  hola\n\n   mundo  "));
    assertEquals("", PostPublishedPushEventListener.snippet(null));
  }

  @Test
  void rejectsNullEvent() {
    assertThrows(NullPointerException.class, () -> listener.on(null));
    verifyNoInteractions(notifyFollowers);
  }

  @Test
  void constructorRejectsNullUseCase() {
    assertThrows(NullPointerException.class, () -> new PostPublishedPushEventListener(null));
    verifyNoInteractions(notifyFollowers);
  }
}
