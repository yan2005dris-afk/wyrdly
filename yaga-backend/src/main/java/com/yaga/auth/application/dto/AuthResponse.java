package com.yaga.auth.application.dto;

public record AuthResponse(String token, String refreshToken, long expiresIn, UserDto user) {}
