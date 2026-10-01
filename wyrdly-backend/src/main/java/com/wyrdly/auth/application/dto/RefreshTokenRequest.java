package com.wyrdly.auth.application.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
    @NotBlank(message = "Refresh token is required") String refreshToken) {}
