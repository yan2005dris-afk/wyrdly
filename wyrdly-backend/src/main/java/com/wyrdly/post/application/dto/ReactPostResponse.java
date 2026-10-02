package com.wyrdly.post.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.wyrdly.post.domain.model.ReactionStatus;
import com.wyrdly.post.domain.model.ReactionType;

/**
 * Response payload for {@code POST /api/posts/{postId}/react}. {@code reactionType} is {@code null}
 * when {@code status} is {@link ReactionStatus#REMOVED}.
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ReactPostResponse {
  private final ReactionStatus status;
  private final ReactionType reactionType;
  private final long totalReactions;

  public ReactPostResponse(ReactionStatus status, ReactionType reactionType, long totalReactions) {
    this.status = status;
    this.reactionType = reactionType;
    this.totalReactions = totalReactions;
  }

  public ReactionStatus getStatus() {
    return status;
  }

  public ReactionType getReactionType() {
    return reactionType;
  }

  public long getTotalReactions() {
    return totalReactions;
  }
}
