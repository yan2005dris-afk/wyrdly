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
        new FeedPost("pst_1", "Hello feed", "https://example.com/pic.webp", NOW, AUTHOR);

    assertEquals("pst_1", feedPost.id());
    assertEquals("Hello feed", feedPost.content());
    assertEquals("https://example.com/pic.webp", feedPost.mediaUrl());
    assertEquals(NOW, feedPost.createdAt());
    assertEquals(AUTHOR, feedPost.author());
  }

  @Test
  void create_Success_WithoutMediaUrl() {
    FeedPost feedPost = new FeedPost("pst_1", "Text only", null, NOW, AUTHOR);

    assertEquals("pst_1", feedPost.id());
    assertNull(feedPost.mediaUrl());
  }

  @Test
  void create_ThrowsException_WhenIdBlank() {
    assertThrows(
        IllegalArgumentException.class, () -> new FeedPost("", "Content", null, NOW, AUTHOR));
  }

  @Test
  void create_ThrowsException_WhenContentBlank() {
    assertThrows(
        IllegalArgumentException.class, () -> new FeedPost("pst_1", "   ", null, NOW, AUTHOR));
  }

  @Test
  void create_ThrowsException_WhenCreatedAtNull() {
    assertThrows(
        NullPointerException.class, () -> new FeedPost("pst_1", "Content", null, null, AUTHOR));
  }

  @Test
  void create_ThrowsException_WhenAuthorNull() {
    assertThrows(
        NullPointerException.class, () -> new FeedPost("pst_1", "Content", null, NOW, null));
  }
}
