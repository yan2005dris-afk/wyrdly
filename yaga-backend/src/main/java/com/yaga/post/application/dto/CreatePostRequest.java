package com.yaga.post.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePostRequest(
    @NotBlank(message = "Content is required")
        @Size(min = 1, max = 1000, message = "Content must be between 1 and 1000 characters")
        String content,
    String mediaUrl) {}
