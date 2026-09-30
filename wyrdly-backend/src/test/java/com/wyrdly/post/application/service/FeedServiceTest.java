package com.wyrdly.post.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wyrdly.post.application.dto.FeedResponseDto;
import com.wyrdly.post.domain.model.Author;
import com.wyrdly.post.domain.model.FeedPost;
import com.wyrdly.post.domain.repository.PostRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

  @Mock private PostRepository postRepository;

  private FeedService feedService;

  private static final Instant NOW = Instant.parse("2026-09-29T10:00:00Z");
  private static final Author AUTHOR =
      new Author("usr_author", "johndoe", "John Doe", "https://example.com/avatar.jpg");

  @BeforeEach
  void setUp() {
    feedService = new FeedService(postRepository);
  }

  @Test
  void getFeed_Success_WithPosts() {
    FeedPost post1 = new FeedPost("pst_1", "First post", null, NOW, AUTHOR, 5, 2, 0, "LIKE");
    FeedPost post2 =
        new FeedPost(
            "pst_2", "Second post", "https://example.com/p.webp", NOW, AUTHOR, 10, 5, 3, null);

    when(postRepository.findFeedByUserId("usr_me", 1, 20)).thenReturn(List.of(post1, post2));
    when(postRepository.countFeedByUserId("usr_me")).thenReturn(2L);

    FeedResponseDto response = feedService.getFeed("usr_me", 1, 20);

    assertEquals(2, response.data().size());
    assertEquals("pst_1", response.data().get(0).id());
    assertEquals("First post", response.data().get(0).content());
    assertEquals("usr_author", response.data().get(0).author().id());
    assertEquals("johndoe", response.data().get(0).author().username());
    assertEquals(5, response.data().get(0).reactionCounts().likeCount());
    assertEquals(2, response.data().get(0).reactionCounts().loveCount());
    assertEquals(0, response.data().get(0).reactionCounts().celebrateCount());
    assertEquals("LIKE", response.data().get(0).userReaction());
    assertEquals("pst_2", response.data().get(1).id());
    assertEquals(10, response.data().get(1).reactionCounts().likeCount());
    assertNull(response.data().get(1).userReaction());
    assertEquals(1, response.meta().page());
    assertEquals(20, response.meta().pageSize());
    assertEquals(2L, response.meta().totalElements());
    assertEquals(1, response.meta().totalPages());
    assertFalse(response.meta().hasNext());
  }

  @Test
  void getFeed_Success_EmptyFeed() {
    when(postRepository.findFeedByUserId("usr_me", 1, 20)).thenReturn(List.of());
    when(postRepository.countFeedByUserId("usr_me")).thenReturn(0L);

    FeedResponseDto response = feedService.getFeed("usr_me", 1, 20);

    assertTrue(response.data().isEmpty());
    assertEquals(0L, response.meta().totalElements());
    assertEquals(0, response.meta().totalPages());
    assertFalse(response.meta().hasNext());
  }

  @Test
  void getFeed_CalculatesHasNext_WhenMorePagesExist() {
    FeedPost post = new FeedPost("pst_1", "Post", null, NOW, AUTHOR, 0, 0, 0, null);
    when(postRepository.findFeedByUserId("usr_me", 1, 20)).thenReturn(List.of(post));
    when(postRepository.countFeedByUserId("usr_me")).thenReturn(25L);

    FeedResponseDto response = feedService.getFeed("usr_me", 1, 20);

    assertEquals(2, response.meta().totalPages());
    assertTrue(response.meta().hasNext());
  }

  @Test
  void getFeed_HasNextFalse_OnLastPage() {
    FeedPost post = new FeedPost("pst_1", "Post", null, NOW, AUTHOR, 1, 0, 0, "LOVE");
    when(postRepository.findFeedByUserId("usr_me", 2, 20)).thenReturn(List.of(post));
    when(postRepository.countFeedByUserId("usr_me")).thenReturn(25L);

    FeedResponseDto response = feedService.getFeed("usr_me", 2, 20);

    assertEquals(2, response.meta().totalPages());
    assertFalse(response.meta().hasNext());
  }

  @Test
  void getFeed_NormalizesNegativeOrZeroPage() {
    when(postRepository.findFeedByUserId(eq("usr_me"), eq(1), anyInt())).thenReturn(List.of());
    when(postRepository.countFeedByUserId("usr_me")).thenReturn(0L);

    FeedResponseDto response = feedService.getFeed("usr_me", 0, 20);

    assertEquals(1, response.meta().page());
    verify(postRepository).findFeedByUserId("usr_me", 1, 20);
  }

  @Test
  void getFeed_ClampsMaxPageSizeTo50() {
    when(postRepository.findFeedByUserId(eq("usr_me"), eq(1), eq(50))).thenReturn(List.of());
    when(postRepository.countFeedByUserId("usr_me")).thenReturn(0L);

    FeedResponseDto response = feedService.getFeed("usr_me", 1, 100);

    assertEquals(50, response.meta().pageSize());
    verify(postRepository).findFeedByUserId("usr_me", 1, 50);
  }

  @Test
  void getFeed_ThrowsException_WhenUserIdBlank() {
    assertThrows(IllegalArgumentException.class, () -> feedService.getFeed("", 1, 20));
    assertThrows(IllegalArgumentException.class, () -> feedService.getFeed(null, 1, 20));
  }
}
