package com.wyrdly.post.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class RepostContextTest {

  private static final Author REPOSTER = new Author("usr_2", "janedoe", "Jane Doe", null);
  private static final Instant REPOSTED_AT = Instant.parse("2026-10-01T10:00:00Z");

  @Test
  void create_Success() {
    RepostContext context = new RepostContext(REPOSTER, REPOSTED_AT);

    assertEquals(REPOSTER, context.reposter());
    assertEquals(REPOSTED_AT, context.repostedAt());
  }

  @Test
  void create_ThrowsException_WhenReposterNull() {
    assertThrows(NullPointerException.class, () -> new RepostContext(null, REPOSTED_AT));
  }

  @Test
  void create_ThrowsException_WhenRepostedAtNull() {
    assertThrows(NullPointerException.class, () -> new RepostContext(REPOSTER, null));
  }
}
