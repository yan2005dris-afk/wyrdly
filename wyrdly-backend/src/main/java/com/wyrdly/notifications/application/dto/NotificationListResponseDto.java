package com.wyrdly.notifications.application.dto;

import java.util.List;

/** Paginated response for {@code GET /api/notifications}. */
public record NotificationListResponseDto(
    List<NotificationDto> notifications,
    long unreadCount,
    int page,
    int pageSize,
    long totalElements) {}
