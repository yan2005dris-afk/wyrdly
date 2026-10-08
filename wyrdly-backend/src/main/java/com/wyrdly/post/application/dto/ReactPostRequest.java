package com.wyrdly.post.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wyrdly.post.domain.model.ReactionType;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for {@code POST /api/posts/{postId}/react}. The {@code type} field is mandatory;
 * unknown, numeric or differently cased values are rejected by the global enum policy in {@code
 * StrictObjectMapperCustomizer}, so no per-field deserializer is needed.
 */
public record ReactPostRequest(
    @JsonProperty(value = "type", required = true) @NotNull ReactionType type) {}
