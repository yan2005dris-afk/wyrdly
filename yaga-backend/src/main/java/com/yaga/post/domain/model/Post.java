package com.yaga.post.domain.model;

import java.time.Instant;

public record Post(String id, String userId, String content, String mediaUrl, Instant createdAt) {

  private static final int MAX_CONTENT_LENGTH = 1000;
  private static final int MIN_CONTENT_LENGTH = 1;

  public Post {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("id must not be blank");
    }
    if (userId == null || userId.isBlank()) {
      throw new IllegalArgumentException("userId must not be blank");
    }
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("content must not be blank");
    }
    if (content.length() < MIN_CONTENT_LENGTH || content.length() > MAX_CONTENT_LENGTH) {
      throw new IllegalArgumentException(
          String.format(
              "content length must be between %d and %d characters",
              MIN_CONTENT_LENGTH, MAX_CONTENT_LENGTH));
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt must not be null");
    }
  }
}
