package com.wyrdly.post.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PostTest {

  private static final Instant NOW = Instant.now();

  @Test
  void create_Success_WithAllValidFields() {
    Post post = new Post("pst_123", "usr_456", "This is a valid post content", null, NOW);

    assertEquals("pst_123", post.id());
    assertEquals("usr_456", post.userId());
    assertEquals("This is a valid post content", post.content());
    assertEquals(null, post.mediaUrl());
    assertEquals(NOW, post.createdAt());
  }

  @Test
  void create_Success_WithMediaUrl() {
    Post post =
        new Post("pst_123", "usr_456", "Post with media", "https://example.com/image.jpg", NOW);

    assertEquals("https://example.com/image.jpg", post.mediaUrl());
  }

  @Test
  void create_ThrowsIllegalArgumentException_WhenIdIsNull() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> new Post(null, "usr_456", "Valid content", null, NOW));

    assertTrue(exception.getMessage().contains("id must not be blank"));
  }

  @Test
  void create_ThrowsIllegalArgumentException_WhenIdIsBlank() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> new Post("   ", "usr_456", "Valid content", null, NOW));

    assertTrue(exception.getMessage().contains("id must not be blank"));
  }

  @Test
  void create_ThrowsIllegalArgumentException_WhenUserIdIsNull() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> new Post("pst_123", null, "Valid content", null, NOW));

    assertTrue(exception.getMessage().contains("userId must not be blank"));
  }

  @Test
  void create_ThrowsIllegalArgumentException_WhenUserIdIsBlank() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> new Post("pst_123", "  ", "Valid content", null, NOW));

    assertTrue(exception.getMessage().contains("userId must not be blank"));
  }

  @Test
  void create_ThrowsIllegalArgumentException_WhenContentIsNull() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> new Post("pst_123", "usr_456", null, null, NOW));

    assertTrue(exception.getMessage().contains("content must not be blank"));
  }

  @Test
  void create_ThrowsIllegalArgumentException_WhenContentIsBlank() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> new Post("pst_123", "usr_456", "   ", null, NOW));

    assertTrue(exception.getMessage().contains("content must not be blank"));
  }

  @Test
  void create_ThrowsIllegalArgumentException_WhenContentIsTooShort() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> new Post("pst_123", "usr_456", "", null, NOW));

    assertTrue(exception.getMessage().contains("content must not be blank"));
  }

  @Test
  void create_ThrowsIllegalArgumentException_WhenContentExceedsMaxLength() {
    String tooLongContent = "a".repeat(1001);
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> new Post("pst_123", "usr_456", tooLongContent, null, NOW));

    assertTrue(exception.getMessage().contains("content length must be between"));
    assertTrue(exception.getMessage().contains("1 and 1000"));
  }

  @Test
  void create_Success_WithMaxLengthContent() {
    String maxContent = "a".repeat(1000);
    Post post = new Post("pst_123", "usr_456", maxContent, null, NOW);

    assertEquals(maxContent, post.content());
  }

  @Test
  void create_Success_WithMinimumLengthContent() {
    Post post = new Post("pst_123", "usr_456", "a", null, NOW);

    assertEquals("a", post.content());
  }

  @Test
  void create_ThrowsIllegalArgumentException_WhenCreatedAtIsNull() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> new Post("pst_123", "usr_456", "Valid content", null, null));

    assertTrue(exception.getMessage().contains("createdAt must not be null"));
  }
}
