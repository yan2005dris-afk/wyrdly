package com.wyrdly.post.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Response DTO for {@code GET /api/posts} and {@code GET /api/feed}.
 *
 * <p>Includes {@code reactionCounts} and {@code userReaction} to support HU08 (Feed) and HU09
 * (Reacciones) interactions. {@code userReaction} is null when the authenticated user has not
 * reacted.
 */
public record PostResponse(
    String id,
    String content,
    String mediaUrl,
    Instant createdAt,
    AuthorDto author,
    ReactionCounts reactionCounts,
    String userReaction) {

  public record AuthorDto(String id, String username, String fullName, String avatarUrl) {}

  public record ReactionCounts(long likeCount, long loveCount, long celebrateCount) {}
}
