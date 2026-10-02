package com.wyrdly.post.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link ReactionType} is deserialized from the JSON {@code type} field of {@code
 * POST /api/posts/{postId}/react} and that unknown values raise a 400-mappable Jackson exception.
 */
class ReactionTypeTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void deserializes_ValidJsonToEnum_Like() throws Exception {
    String json = "{\"type\":\"LIKE\"}";
    ReactionTypeHolder holder = mapper.readValue(json, ReactionTypeHolder.class);
    assertEquals(ReactionType.LIKE, holder.getType());
  }

  @Test
  void deserializes_ValidJsonToEnum_Love() throws Exception {
    String json = "{\"type\":\"LOVE\"}";
    ReactionTypeHolder holder = mapper.readValue(json, ReactionTypeHolder.class);
    assertEquals(ReactionType.LOVE, holder.getType());
  }

  @Test
  void deserializes_ValidJsonToEnum_Celebrate() throws Exception {
    String json = "{\"type\":\"CELEBRATE\"}";
    ReactionTypeHolder holder = mapper.readValue(json, ReactionTypeHolder.class);
    assertEquals(ReactionType.CELEBRATE, holder.getType());
  }

  @Test
  void deserialization_FailsOnUnknownValue() {
    String json = "{\"type\":\"DISLIKE\"}";
    assertThrows(
        InvalidFormatException.class, () -> mapper.readValue(json, ReactionTypeHolder.class));
  }

  @Test
  void deserialization_FailsOnLowercaseValue() {
    String json = "{\"type\":\"like\"}";
    assertThrows(
        InvalidFormatException.class, () -> mapper.readValue(json, ReactionTypeHolder.class));
  }

  /** Simple holder mirroring {@code ReactPostRequest} for Jackson deserialization testing. */
  public static class ReactionTypeHolder {
    private ReactionType type;

    public ReactionType getType() {
      return type;
    }

    public void setType(ReactionType type) {
      this.type = type;
    }
  }
}
