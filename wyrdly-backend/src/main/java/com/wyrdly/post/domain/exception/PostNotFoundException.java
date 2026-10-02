package com.wyrdly.post.domain.exception;

import java.util.Objects;

/**
 * Raised when a post referenced by id does not exist in the graph. Mapped to {@code 404 Not Found}
 * by {@code PostExceptionMappers}.
 */
public class PostNotFoundException extends RuntimeException {

  private final String postId;

  public PostNotFoundException(String postId) {
    super("Post not found: " + Objects.requireNonNull(postId, "postId must not be null"));
    this.postId = postId;
  }

  public String postId() {
    return postId;
  }
}
