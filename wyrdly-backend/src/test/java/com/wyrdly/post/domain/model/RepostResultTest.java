package com.wyrdly.post.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RepostResultTest {

  @Test
  void create_Success() {
    RepostResult result = new RepostResult("pst_1", true, true, 5L, "usr_author");
    assertEquals("pst_1", result.postId());
    assertTrue(result.reposted());
    assertTrue(result.changed());
    assertEquals(5L, result.repostsCount());
    assertEquals("usr_author", result.authorId());
  }

  @Test
  void create_Success_WhenUnreposted() {
    RepostResult result = new RepostResult("pst_1", false, true, 4L, "usr_author");
    assertEquals("pst_1", result.postId());
    assertFalse(result.reposted());
    assertTrue(result.changed());
    assertEquals(4L, result.repostsCount());
  }

  @Test
  void create_ThrowsException_WhenPostIdNullOrBlank() {
    assertThrows(
        NullPointerException.class, () -> new RepostResult(null, true, true, 1L, "usr_author"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new RepostResult("   ", true, true, 1L, "usr_author"));
  }

  @Test
  void create_ThrowsException_WhenAuthorIdNullOrBlank() {
    assertThrows(NullPointerException.class, () -> new RepostResult("pst_1", true, true, 1L, null));
    assertThrows(
        IllegalArgumentException.class, () -> new RepostResult("pst_1", true, true, 1L, "   "));
  }

  @Test
  void create_ThrowsException_WhenRepostsCountNegative() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new RepostResult("pst_1", true, true, -1L, "usr_author"));
  }
}
