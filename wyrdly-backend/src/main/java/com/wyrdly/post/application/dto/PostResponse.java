package com.wyrdly.post.application.dto;

import java.time.Instant;

/**
 * Response DTO for {@code POST /api/posts}.
 *
 * <p>{@code reactionCounts} and {@code userReaction} are intentionally omitted until HU09
 * (Reacciones) lands. Returning hardcoded {@code {LIKE: 0}} / {@code null} values would be
 * misleading: clients would build features against fields that have no backing logic.
 */
public record PostResponse(
    String id, String content, String mediaUrl, Instant createdAt, AuthorDto author) {

  public record AuthorDto(String id, String username, String fullName, String avatarUrl) {}
}
