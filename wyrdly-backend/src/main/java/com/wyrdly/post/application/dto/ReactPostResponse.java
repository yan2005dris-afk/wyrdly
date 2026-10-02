package com.wyrdly.post.application.dto;

import com.wyrdly.post.domain.model.ReactionStatus;
import com.wyrdly.post.domain.model.ReactionType;

/**
 * Response payload for {@code POST /api/posts/{postId}/react}. {@code reactionType} is {@code null}
 * when {@code status} is {@link ReactionStatus#REMOVED}.
 */
public record ReactPostResponse(
    ReactionStatus status, ReactionType reactionType, long totalReactions) {}
