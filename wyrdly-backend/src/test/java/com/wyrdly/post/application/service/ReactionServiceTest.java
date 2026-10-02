package com.wyrdly.post.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wyrdly.post.domain.exception.PostNotFoundException;
import com.wyrdly.post.domain.model.ReactionResult;
import com.wyrdly.post.domain.model.ReactionStatus;
import com.wyrdly.post.domain.model.ReactionType;
import com.wyrdly.post.domain.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ReactionService}. The service is a thin pass-through to the repository; the
 * suite verifies the mapping contract and exception propagation.
 */
class ReactionServiceTest {

  private PostRepository postRepository;
  private ReactionService reactionService;

  @BeforeEach
  void setUp() {
    postRepository = mock(PostRepository.class);
    reactionService = new ReactionService(postRepository);
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
}
