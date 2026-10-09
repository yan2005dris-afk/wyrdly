package com.wyrdly.post.domain.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PostRepostedEventTest {

  @Test
  void create_Success() {
    PostRepostedEvent event = new PostRepostedEvent("pst_1", "usr_author", "usr_actor");
    assertEquals("pst_1", event.postId());
    assertEquals("usr_author", event.postAuthorId());
    assertEquals("usr_actor", event.actorId());
  }

  @Test
  void create_ThrowsException_WhenPostIdNullOrBlank() {
    assertThrows(
        NullPointerException.class, () -> new PostRepostedEvent(null, "usr_author", "usr_actor"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PostRepostedEvent("   ", "usr_author", "usr_actor"));
  }

  @Test
  void create_ThrowsException_WhenPostAuthorIdNullOrBlank() {
    assertThrows(
        NullPointerException.class, () -> new PostRepostedEvent("pst_1", null, "usr_actor"));
    assertThrows(
        IllegalArgumentException.class, () -> new PostRepostedEvent("pst_1", "   ", "usr_actor"));
  }

  @Test
  void create_ThrowsException_WhenActorIdNullOrBlank() {
    assertThrows(
        NullPointerException.class, () -> new PostRepostedEvent("pst_1", "usr_author", null));
    assertThrows(
        IllegalArgumentException.class, () -> new PostRepostedEvent("pst_1", "usr_author", "   "));
  }
}
