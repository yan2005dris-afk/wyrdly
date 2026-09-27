package com.yaga.user.application.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @Size(max = 100, message = "Full name must be at most 100 characters") String fullName,
    @Size(max = 250, message = "Bio must be at most 250 characters") String bio,
    @Size(max = 2048, message = "Avatar URL must be at most 2048 characters")
        @Pattern(
            regexp = "^https?://.+",
            message = "Avatar URL must start with http:// or https://")
        String avatarUrl) {}
