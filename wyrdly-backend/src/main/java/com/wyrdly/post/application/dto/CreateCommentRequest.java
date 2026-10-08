package com.wyrdly.post.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Inbound DTO for {@code POST /api/posts/{postId}/comments}. Validation runs at the JAX-RS layer
 * via {@code @Valid} in the resource; violations produce a {@code 400 Bad Request} through {@code
 * PostExceptionMappers.handleConstraintViolation}.
 */
public record CreateCommentRequest(
    @NotBlank(message = "El contenido no puede estar vacío")
        @Size(max = 1000, message = "El comentario no puede superar los 1000 caracteres")
        String content) {}
