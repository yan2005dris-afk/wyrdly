package com.wyrdly.notifications.application.dto;

/** Response payload for {@code GET /api/notifications/vapid-public-key}. */
public record VapidPublicKeyResponseDto(String publicKey) {}
