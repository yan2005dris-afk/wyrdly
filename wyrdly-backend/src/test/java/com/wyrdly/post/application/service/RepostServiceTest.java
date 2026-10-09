package com.wyrdly.post.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wyrdly.post.application.dto.RepostResponse;
import com.wyrdly.post.domain.event.PostRepostedEvent;
import com.wyrdly.post.domain.model.RepostResult;
import com.wyrdly.post.domain.repository.RepostRepository;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RepostServiceTest {

  private RepostRepository repostRepository;
  private Event<PostRepostedEvent> postRepostedEvent;
  private RepostService repostService;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    repostRepository = mock(RepostRepository.class);
    postRepostedEvent = mock(Event.class);
    repostService = new RepostService(repostRepository, postRepostedEvent);
  }

  @Test
  void repost_FiresEvent_WhenChangedAndRepostedAndNotSelfRepost() {
    String userId = "usr_actor";
    String postId = "pst_1";
    String authorId = "usr_author";

    when(repostRepository.repost(eq(userId), eq(postId), any()))
        .thenReturn(new RepostResult(postId, true, true, 1L, authorId));

    RepostResponse response = repostService.repost(userId, postId);

    assertTrue(response.reposted());
    assertEquals(1L, response.repostsCount());

    ArgumentCaptor<PostRepostedEvent> captor = ArgumentCaptor.forClass(PostRepostedEvent.class);
    verify(postRepostedEvent).fire(captor.capture());
    PostRepostedEvent fired = captor.getValue();
    assertEquals(postId, fired.postId());
    assertEquals(authorId, fired.postAuthorId());
    assertEquals(userId, fired.actorId());
  }

  @Test
  void repost_DoesNotFireEvent_WhenSelfRepost() {
    String userId = "usr_author";
    String postId = "pst_1";

    when(repostRepository.repost(eq(userId), eq(postId), any()))
        .thenReturn(new RepostResult(postId, true, true, 1L, userId));

    RepostResponse response = repostService.repost(userId, postId);

    assertTrue(response.reposted());
    assertEquals(1L, response.repostsCount());
    verify(postRepostedEvent, never()).fire(any());
  }

  @Test
  void repost_DoesNotFireEvent_WhenNotChanged() {
    String userId = "usr_actor";
    String postId = "pst_1";
    String authorId = "usr_author";

    when(repostRepository.repost(eq(userId), eq(postId), any()))
        .thenReturn(new RepostResult(postId, true, false, 1L, authorId));

    RepostResponse response = repostService.repost(userId, postId);

    assertTrue(response.reposted());
    assertEquals(1L, response.repostsCount());
    verify(postRepostedEvent, never()).fire(any());
  }

  @Test
  void unrepost_NeverFiresEvent() {
    String userId = "usr_actor";
    String postId = "pst_1";
    String authorId = "usr_author";

    when(repostRepository.unrepost(userId, postId))
        .thenReturn(new RepostResult(postId, false, true, 0L, authorId));

    RepostResponse response = repostService.unrepost(userId, postId);

    assertFalse(response.reposted());
    assertEquals(0L, response.repostsCount());
    verify(postRepostedEvent, never()).fire(any());
  }

  @Test
  void repost_ThrowsException_WhenArgumentsBlankOrNull() {
    assertThrows(IllegalArgumentException.class, () -> repostService.repost(null, "pst_1"));
    assertThrows(IllegalArgumentException.class, () -> repostService.repost("   ", "pst_1"));
    assertThrows(IllegalArgumentException.class, () -> repostService.repost("u1", null));
    assertThrows(IllegalArgumentException.class, () -> repostService.repost("u1", "   "));
  }

  @Test
  void unrepost_ThrowsException_WhenArgumentsBlankOrNull() {
    assertThrows(IllegalArgumentException.class, () -> repostService.unrepost(null, "pst_1"));
    assertThrows(IllegalArgumentException.class, () -> repostService.unrepost("   ", "pst_1"));
    assertThrows(IllegalArgumentException.class, () -> repostService.unrepost("u1", null));
    assertThrows(IllegalArgumentException.class, () -> repostService.unrepost("u1", "   "));
  }
}
