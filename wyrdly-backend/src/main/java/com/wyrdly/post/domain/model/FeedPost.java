package com.wyrdly.post.domain.model;

import java.time.Instant;
import java.util.Objects;

/** Domain entity representing a post enriched with its author information for feed presentation. */
public record FeedPost(
    String id, String content, String mediaUrl, Instant createdAt, Author author) {

  public FeedPost {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("id must not be blank");
    }
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("content must not be blank");
    }
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    Objects.requireNonNull(author, "author must not be null");
  }
}
