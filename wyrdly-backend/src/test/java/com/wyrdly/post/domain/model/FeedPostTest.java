package com.wyrdly.post.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class FeedPostTest {

  private static final Instant NOW = Instant.now();
  private static final Author AUTHOR =
      new Author("usr_1", "johndoe", "John Doe", "https://example.com/avatar.jpg");

  @Test
  void create_Success_WithAllFields() {
    FeedPost feedPost =
        new FeedPost(
            "pst_1", "Hello feed", "https://example.com/pic.webp", NOW, AUTHOR, 5, 3, 1, 4, "LIKE");

    assertEquals("pst_1", feedPost.id());
    assertEquals("Hello feed", feedPost.content());
    assertEquals("https://example.com/pic.webp", feedPost.mediaUrl());
    assertEquals(NOW, feedPost.createdAt());
    assertEquals(AUTHOR, feedPost.author());
    assertEquals(5, feedPost.likeCount());
    assertEquals(3, feedPost.loveCount());
    assertEquals(1, feedPost.celebrateCount());
    assertEquals(4L, feedPost.commentsCount());
    assertEquals("LIKE", feedPost.userReaction());
  }

  @Test
  void create_Success_WithoutMediaUrl() {
    FeedPost feedPost = new FeedPost("pst_1", "Text only", null, NOW, AUTHOR, 0, 0, 0, 0L, null);

    assertEquals("pst_1", feedPost.id());
    assertNull(feedPost.mediaUrl());
    assertEquals(0, feedPost.likeCount());
    assertEquals(0L, feedPost.commentsCount());
    assertNull(feedPost.userReaction());
  }

  @Test
  void create_ThrowsException_WhenIdBlank() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new FeedPost("", "Content", null, NOW, AUTHOR, 0, 0, 0, 0L, null));
  }

  @Test
  void create_ThrowsException_WhenContentBlank() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new FeedPost("pst_1", "   ", null, NOW, AUTHOR, 0, 0, 0, 0L, null));
  }

  @Test
  void create_ThrowsException_WhenCreatedAtNull() {
    assertThrows(
        NullPointerException.class,
        () -> new FeedPost("pst_1", "Content", null, null, AUTHOR, 0, 0, 0, 0L, null));
  }

  @Test
  void create_ThrowsException_WhenAuthorNull() {
    assertThrows(
        NullPointerException.class,
        () -> new FeedPost("pst_1", "Content", null, NOW, null, 0, 0, 0, 0L, null));
  }

  @Test
  void create_ThrowsException_WhenReactionCountsNegative() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new FeedPost("pst_1", "Content", null, NOW, AUTHOR, -1, 0, 0, 0L, null));
    assertThrows(
        IllegalArgumentException.class,
        () -> new FeedPost("pst_1", "Content", null, NOW, AUTHOR, 0, -1, 0, 0L, null));
    assertThrows(
        IllegalArgumentException.class,
        () -> new FeedPost("pst_1", "Content", null, NOW, AUTHOR, 0, 0, -1, 0L, null));
  }

  @Test
  void create_ThrowsException_WhenCommentsCountNegative() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new FeedPost("pst_1", "Content", null, NOW, AUTHOR, 0, 0, 0, -1L, null));
  }

  @Test
  void create_Success_WithRepostsFields() {
    FeedPost feedPost =
        new FeedPost("pst_1", "Hello feed", null, NOW, AUTHOR, 5, 3, 1, 4L, 12L, "LIKE", true);

    assertEquals(12L, feedPost.repostsCount());
    assertEquals(true, feedPost.userHasReposted());
  }

  @Test
  void create_ThrowsException_WhenRepostsCountNegative() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new FeedPost("pst_1", "Content", null, NOW, AUTHOR, 0, 0, 0, 0L, -1L, null, false));
  }

  @Test
  void isRepost_False_WhenNoRepostContext() {
    FeedPost feedPost =
        new FeedPost("pst_1", "Content", null, NOW, AUTHOR, 0, 0, 0, 0L, 0L, null, false);

    assertNull(feedPost.repostContext());
    assertEquals(false, feedPost.isRepost());
  }

  @Test
  void isRepost_True_WhenRepostContextPresent() {
    Author reposter = new Author("usr_2", "janedoe", "Jane Doe", null);
    RepostContext context = new RepostContext(reposter, NOW);

    FeedPost feedPost =
        new FeedPost("pst_1", "Content", null, NOW, AUTHOR, 0, 0, 0, 0L, 1L, null, false, context);

    assertEquals(true, feedPost.isRepost());
    assertEquals(context, feedPost.repostContext());
    assertEquals(AUTHOR, feedPost.author());
  }
}
