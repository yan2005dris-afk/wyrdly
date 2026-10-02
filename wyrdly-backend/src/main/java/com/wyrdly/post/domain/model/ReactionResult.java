package com.wyrdly.post.domain.model;

import java.util.Objects;

/**
 * Domain result of a reaction toggle operation. {@link #reactionType()} is null when the status is
 * {@link ReactionStatus#REMOVED}.
 */
public record ReactionResult(
    String postId, ReactionStatus status, ReactionType reactionType, long totalReactions) {

  public ReactionResult {
    Objects.requireNonNull(postId, "postId must not be null");
    Objects.requireNonNull(status, "status must not be null");
    if (postId.isBlank()) {
      throw new IllegalArgumentException("postId must not be blank");
    }
    if (totalReactions < 0) {
      throw new IllegalArgumentException("totalReactions must not be negative");
    }
    if (status == ReactionStatus.REMOVED && reactionType != null) {
      throw new IllegalArgumentException("reactionType must be null when status is REMOVED");
    }
    if (status != ReactionStatus.REMOVED && reactionType == null) {
      throw new IllegalArgumentException("reactionType is required when status is not REMOVED");
    }
  }
}
