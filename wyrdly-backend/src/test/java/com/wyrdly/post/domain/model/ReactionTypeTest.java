package com.wyrdly.post.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.wyrdly.post.application.dto.ReactPostRequest;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link ReactionType} in {@link ReactPostRequest} is deserialized from the JSON
 * {@code type} field of {@code POST /api/posts/{postId}/react} and that unknown values raise a
 * 400-mappable Jackson exception.
 */
class ReactionTypeTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void deserializes_ValidJsonToEnum_Like() throws Exception {
    String json = "{\"type\":\"LIKE\"}";
    ReactPostRequest req = mapper.readValue(json, ReactPostRequest.class);
    assertEquals(ReactionType.LIKE, req.type());
  }

  @Test
  void deserializes_ValidJsonToEnum_Love() throws Exception {
    String json = "{\"type\":\"LOVE\"}";
    ReactPostRequest req = mapper.readValue(json, ReactPostRequest.class);
    assertEquals(ReactionType.LOVE, req.type());
  }

  @Test
  void deserializes_ValidJsonToEnum_Celebrate() throws Exception {
    String json = "{\"type\":\"CELEBRATE\"}";
    ReactPostRequest req = mapper.readValue(json, ReactPostRequest.class);
    assertEquals(ReactionType.CELEBRATE, req.type());
  }

  @Test
  void deserialization_FailsOnUnknownValue() {
    String json = "{\"type\":\"DISLIKE\"}";
    assertThrows(
        InvalidFormatException.class, () -> mapper.readValue(json, ReactPostRequest.class));
  }

  @Test
  void deserialization_FailsOnLowercaseValue() {
    String json = "{\"type\":\"like\"}";
    assertThrows(
        InvalidFormatException.class, () -> mapper.readValue(json, ReactPostRequest.class));
  }
}
