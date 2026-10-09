package com.wyrdly.post.application.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wyrdly.post.application.dto.PostResponse.AuthorDto;
import com.wyrdly.post.application.dto.PostResponse.ReactionCounts;
import com.wyrdly.post.application.dto.PostResponse.RepostContextDto;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PostResponseTest {

  private static final Instant CREATED_AT = Instant.parse("2026-10-01T08:00:00Z");
  private static final Instant REPOSTED_AT = Instant.parse("2026-10-02T09:30:00Z");
  private static final AuthorDto AUTHOR = new AuthorDto("usr_1", "alice", "Alice Doe", null);
  private static final ReactionCounts COUNTS = new ReactionCounts(1, 2, 3);

  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  @Test
  void legacyConstructors_DefaultRepostContextToNull() {
    PostResponse tenArgs =
        new PostResponse("pst_1", "Hola", null, CREATED_AT, AUTHOR, COUNTS, 4L, 5L, "LIKE", true);
    PostResponse eightArgs =
        new PostResponse("pst_1", "Hola", null, CREATED_AT, AUTHOR, COUNTS, 4L, "LIKE");

    assertNull(tenArgs.repostContext());
    assertEquals(5L, tenArgs.repostsCount());
    assertNull(eightArgs.repostContext());
    assertEquals(0L, eightArgs.repostsCount());
    assertFalse(eightArgs.userHasReposted());
  }

  @Test
  void serialization_IncludesRepostContext_WhenPresent() {
    PostResponse response =
        new PostResponse(
            "pst_1",
            "Hola",
            null,
            CREATED_AT,
            AUTHOR,
            COUNTS,
            0L,
            1L,
            null,
            false,
            new RepostContextDto("usr_2", "bob", "Bob Smith", null, REPOSTED_AT));

    JsonNode json = objectMapper.valueToTree(response);

    assertEquals("usr_1", json.get("author").get("id").asText());
    JsonNode context = json.get("repostContext");
    assertEquals("usr_2", context.get("reposterId").asText());
    assertEquals("bob", context.get("reposterUsername").asText());
    assertEquals("Bob Smith", context.get("reposterName").asText());
    assertEquals("2026-10-02T09:30:00Z", context.get("repostedAt").asText());
  }

  @Test
  void serialization_RepostContextIsNull_ForOriginalPublication() {
    PostResponse response =
        new PostResponse("pst_1", "Hola", null, CREATED_AT, AUTHOR, COUNTS, 0L, 0L, null, false);

    JsonNode json = objectMapper.valueToTree(response);

    assertTrue(json.get("repostContext").isNull());
  }
}
