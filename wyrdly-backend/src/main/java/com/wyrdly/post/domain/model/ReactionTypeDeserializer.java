package com.wyrdly.post.domain.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

/**
 * Custom deserializer for {@link ReactionType} that fails on unknown enum values instead of
 * returning null. This ensures that invalid reaction types are rejected with a 400 Bad Request
 * status.
 */
public class ReactionTypeDeserializer extends JsonDeserializer<ReactionType> {

  @Override
  public ReactionType deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    String value = p.getText();
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("ReactionType cannot be null or empty");
    }
    try {
      return ReactionType.valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new com.fasterxml.jackson.databind.exc.InvalidFormatException(
          p,
          String.format("Invalid ReactionType: '%s'. Allowed values are: LIKE, LOVE, CELEBRATE", value),
          value,
          ReactionType.class);
    }
  }
}
