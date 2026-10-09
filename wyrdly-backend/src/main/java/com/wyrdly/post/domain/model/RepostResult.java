package com.wyrdly.post.domain.model;

import java.util.Objects;

/**
 * Domain outcome of a repost / boost toggle operation on a post.
 *
 * @param postId the identifier of the post being reposted or unreposted
 * @param reposted true if the user currently has a repost on the post after this operation
 * @param changed true if the database state changed as part of this operation (e.g. newly created
 *     or deleted)
 * @param repostsCount total number of reposts on the post after this operation
 * @param authorId identifier of the author who published the post
 */
public record RepostResult(
    String postId, boolean reposted, boolean changed, long repostsCount, String authorId) {

  public RepostResult {
    Objects.requireNonNull(postId, "postId must not be null");
    Objects.requireNonNull(authorId, "authorId must not be null");
    if (postId.isBlank()) {
      throw new IllegalArgumentException("postId must not be blank");
    }
    if (authorId.isBlank()) {
      throw new IllegalArgumentException("authorId must not be blank");
    }
    if (repostsCount < 0) {
      throw new IllegalArgumentException("repostsCount must not be negative");
    }
  }
}
