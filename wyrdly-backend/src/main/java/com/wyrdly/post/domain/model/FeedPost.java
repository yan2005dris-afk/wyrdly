package com.wyrdly.post.domain.model;

import java.time.Instant;
import java.util.Objects;

/** Domain entity representing a post enriched with its author information and reactions for feed presentation. */
public record FeedPost(
    String id,
    String content,
    String mediaUrl,
    Instant createdAt,
    Author author,
    long likeCount,
    long loveCount,
    long celebrateCount,
    String userReaction) {

  public FeedPost {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("id must not be blank");
    }
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("content must not be blank");
    }
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    Objects.requireNonNull(author, "author must not be null");
    if (likeCount < 0 || loveCount < 0 || celebrateCount < 0) {
      throw new IllegalArgumentException("reaction counts must not be negative");
    }
    // userReaction can be null (user has not reacted) or one of: LIKE, LOVE, CELEBRATE
  }
}
