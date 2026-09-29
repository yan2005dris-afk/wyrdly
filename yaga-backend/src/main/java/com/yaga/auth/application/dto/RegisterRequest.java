package com.yaga.auth.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "Username is required")
        @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
        @Pattern(
            regexp = "^[a-zA-Z0-9_]+$",
            message = "Username can only contain alphanumeric characters and underscores")
        String username,
    @NotBlank(message = "Email is required") @Email(message = "Email must be a valid email address")
        String email,
    @NotBlank(message = "Password is required")
        @Size(min = 6, max = 100, message = "Password must be at least 6 characters")
        String password,
    @NotBlank(message = "Full name is required")
        @Size(max = 100, message = "Full name must be at most 100 characters")
        String fullName,
    String bio,
    String avatarUrl) {}
