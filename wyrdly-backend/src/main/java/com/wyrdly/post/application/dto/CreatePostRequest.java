package com.wyrdly.post.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record CreatePostRequest(
    @NotBlank(message = "Content is required")
        @Size(min = 1, max = 1000, message = "Content must be between 1 and 1000 characters")
        String content,
    @URL(message = "mediaUrl must be a valid URL")
        @Pattern(regexp = "^https?://.*", message = "mediaUrl must use http or https protocol")
        String mediaUrl) {}
