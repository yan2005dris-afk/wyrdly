package com.wyrdly.infrastructure.jackson;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Configures Jackson to reject invalid enum values and missing required fields. This ensures that
 * REST endpoints with {@code @Valid} constraints properly fail on invalid input.
 */
@ApplicationScoped
public class StrictObjectMapperCustomizer implements ObjectMapperCustomizer {

  @Override
  public void customize(ObjectMapper objectMapper) {
    // Reject unknown properties (already set in application.properties)
    objectMapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    // Reject requests with missing required creator properties (fields in records/constructors)
    objectMapper.enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES);

    // Reject invalid enum values (not in the enum definition)
    objectMapper.disable(DeserializationFeature.READ_ENUMS_USING_TO_STRING);
    objectMapper.enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS);
  }
}
