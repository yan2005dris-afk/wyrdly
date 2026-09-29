package com.wyrdly.auth.domain.model;

public record AuthTokens(String accessToken, String refreshToken, long expiresIn) {}
