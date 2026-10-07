package com.wyrdly.post.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wyrdly.post.domain.event.PostReactionEvent;
import com.wyrdly.post.domain.exception.PostNotFoundException;
import com.wyrdly.post.domain.model.Post;
import com.wyrdly.post.domain.model.ReactionResult;
import com.wyrdly.post.domain.model.ReactionStatus;
import com.wyrdly.post.domain.model.ReactionType;
import com.wyrdly.post.domain.repository.PostRepository;
import jakarta.enterprise.event.Event;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ReactionService}. The service is a thin pass-through to the repository; the
 * suite verifies the mapping contract and exception propagation.
 */
class ReactionServiceTest {

  private PostRepository postRepository;

  @SuppressWarnings("unchecked")
  private Event<PostReactionEvent> reactionEvent = mock(Event.class);

  private ReactionService reactionService;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    postRepository = mock(PostRepository.class);
    reactionEvent = mock(Event.class);
    reactionService = new ReactionService(postRepository, reactionEvent);
  }

  @Test
  void react_CallsRepositoryWithCorrectArgs() {
    String userId = "usr_alice";
    String postId = "pst_42";
    ReactionType type = ReactionType.LIKE;

    when(postRepository.react(eq(userId), eq(postId), eq(type)))
        .thenReturn(new ReactionResult(postId, ReactionStatus.ADDED, type, 1));

    ReactionResult result = reactionService.react(userId, postId, type);

    assertNotNull(result);
    assertEquals(ReactionStatus.ADDED, result.status());
    assertEquals(type, result.reactionType());
    verify(postRepository).react(userId, postId, type);
  }

  @Test
  void react_ReturnsAddedResult_FromRepository() {
    ReactionResult expected =
        new ReactionResult("pst_1", ReactionStatus.ADDED, ReactionType.LOVE, 7);
    when(postRepository.react(any(), any(), any())).thenReturn(expected);

    ReactionResult actual = reactionService.react("u1", "pst_1", ReactionType.LOVE);

    assertEquals(expected, actual);
    assertEquals(7, actual.totalReactions());
  }

  @Test
  void react_ReturnsRemovedResult_FromRepository() {
    ReactionResult expected = new ReactionResult("pst_1", ReactionStatus.REMOVED, null, 6);
    when(postRepository.react(any(), any(), any())).thenReturn(expected);

    ReactionResult actual = reactionService.react("u1", "pst_1", ReactionType.LIKE);

    assertEquals(ReactionStatus.REMOVED, actual.status());
    assertNull(actual.reactionType());
    assertEquals(6, actual.totalReactions());
  }

  @Test
  void react_ReturnsUpdatedResult_FromRepository() {
    ReactionResult expected =
        new ReactionResult("pst_1", ReactionStatus.UPDATED, ReactionType.CELEBRATE, 8);
    when(postRepository.react(any(), any(), any())).thenReturn(expected);

    ReactionResult actual = reactionService.react("u1", "pst_1", ReactionType.CELEBRATE);

    assertEquals(ReactionStatus.UPDATED, actual.status());
    assertEquals(ReactionType.CELEBRATE, actual.reactionType());
    assertEquals(8, actual.totalReactions());
  }

  @Test
  void react_PropagatesPostNotFoundException_FromRepository() {
    when(postRepository.react(any(), any(), any())).thenThrow(new PostNotFoundException("missing"));

    assertThrows(
        PostNotFoundException.class,
        () -> reactionService.react("u1", "missing", ReactionType.LIKE));
  }

  @Test
  void react_FiresPostReactionEvent_WhenReactionIsAdded() {
    String reactor = "usr_reactor";
    String author = "usr_author";
    String postId = "pst_1";
    when(postRepository.react(eq(reactor), eq(postId), eq(ReactionType.LIKE)))
        .thenReturn(new ReactionResult(postId, ReactionStatus.ADDED, ReactionType.LIKE, 1));
    when(postRepository.findById(postId))
        .thenReturn(Optional.of(new Post(postId, author, "hola", null, Instant.now())));

    reactionService.react(reactor, postId, ReactionType.LIKE);

    verify(reactionEvent).fire(new PostReactionEvent(postId, author, reactor, ReactionType.LIKE));
  }

  @Test
  void react_FiresPostReactionEvent_WhenReactionIsUpdated() {
    String reactor = "usr_reactor";
    String author = "usr_author";
    String postId = "pst_2";
    when(postRepository.react(eq(reactor), eq(postId), eq(ReactionType.CELEBRATE)))
        .thenReturn(new ReactionResult(postId, ReactionStatus.UPDATED, ReactionType.CELEBRATE, 4));
    when(postRepository.findById(postId))
        .thenReturn(Optional.of(new Post(postId, author, "feliz", null, Instant.now())));

    reactionService.react(reactor, postId, ReactionType.CELEBRATE);

    verify(reactionEvent)
        .fire(new PostReactionEvent(postId, author, reactor, ReactionType.CELEBRATE));
  }

  @Test
  void react_DoesNotFireEvent_WhenReactionIsRemoved() {
    String reactor = "usr_reactor";
    String postId = "pst_3";
    when(postRepository.react(eq(reactor), eq(postId), eq(ReactionType.LIKE)))
        .thenReturn(new ReactionResult(postId, ReactionStatus.REMOVED, null, 0));

    reactionService.react(reactor, postId, ReactionType.LIKE);

    verify(reactionEvent, never()).fire(any());
  }

  @Test
  void react_DoesNotFireEvent_OnSelfReaction() {
    String user = "usr_same";
    String postId = "pst_self";
    when(postRepository.react(eq(user), eq(postId), eq(ReactionType.LOVE)))
        .thenReturn(new ReactionResult(postId, ReactionStatus.ADDED, ReactionType.LOVE, 1));
    when(postRepository.findById(postId))
        .thenReturn(Optional.of(new Post(postId, user, "mi post", null, Instant.now())));

    reactionService.react(user, postId, ReactionType.LOVE);

    verify(reactionEvent, never()).fire(any());
  }

  @Test
  void react_DoesNotFireEvent_WhenPostLookupFailsAfterReact() {
    String reactor = "usr_reactor";
    String postId = "pst_ghost";
    when(postRepository.react(eq(reactor), eq(postId), eq(ReactionType.LIKE)))
        .thenReturn(new ReactionResult(postId, ReactionStatus.ADDED, ReactionType.LIKE, 1));
    when(postRepository.findById(postId)).thenReturn(Optional.empty());

    reactionService.react(reactor, postId, ReactionType.LIKE);

    verify(reactionEvent, never()).fire(any());
  }
}
