package com.wyrdly.post.domain.model;

/**
 * Domain projection of a post author.
 *
 * <p>This is a read-only snapshot of the user fields needed to enrich a post response. The post
 * bounded context does not depend on the auth bounded context's {@code User} model — the
 * infrastructure layer translates between them.
 */
public record Author(String id, String username, String fullName, String avatarUrl) {

  public Author {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("author.id must not be blank");
    }
    if (username == null || username.isBlank()) {
      throw new IllegalArgumentException("author.username must not be blank");
    }
    if (fullName == null || fullName.isBlank()) {
      throw new IllegalArgumentException("author.fullName must not be blank");
    }
    // avatarUrl may legitimately be null/empty
  }
}
