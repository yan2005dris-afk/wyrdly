package com.yaga.auth.domain.model;

public record AuthTokens(String accessToken, String refreshToken, long expiresIn) {}
