package com.wyrdly.post.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.wyrdly.infrastructure.jackson.ReactionTypeDeserializer;
import com.wyrdly.post.domain.model.ReactionType;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for {@code POST /api/posts/{postId}/react}. The {@code type} field is mandatory
 * and Jackson rejects unknown enum values automatically (returning {@code 400} via the framework's
 * default error handler).
 */
public record ReactPostRequest(
    @JsonProperty(value = "type", required = true)
        @JsonDeserialize(using = ReactionTypeDeserializer.class)
        @NotNull
        ReactionType type) {}
