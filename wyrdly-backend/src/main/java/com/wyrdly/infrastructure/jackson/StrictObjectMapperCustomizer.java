package com.wyrdly.infrastructure.jackson;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.inject.Singleton;

/**
 * Single source of truth for the API's JSON contract. Kept in code instead of {@code
 * application.properties} so every feature is compile-checked: Quarkus only exposes a subset of
 * Jackson features as config keys and silently ignores unknown ones.
 */
@Singleton
public class StrictObjectMapperCustomizer implements ObjectMapperCustomizer {

  @Override
  public void customize(ObjectMapper objectMapper) {
    objectMapper
        // Strict deserialization: reject unknown fields, null for primitives (instead of
        // coercing to 0/false) and numeric or unknown enum values. Absent fields deserialize as
        // null on purpose: which fields are required is declared with Bean Validation on each DTO,
        // so optional fields can be omitted and new ones can be added without breaking clients.
        .enable(
            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
            DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
        .disable(DeserializationFeature.READ_ENUMS_USING_TO_STRING)
        // Serialization: allow empty objects, ISO-8601 dates instead of epoch numbers
        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }
}
