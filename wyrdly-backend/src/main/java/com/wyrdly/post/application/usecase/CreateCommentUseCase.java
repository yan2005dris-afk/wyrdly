package com.wyrdly.post.application.usecase;

import com.wyrdly.post.application.dto.CommentResponse;
import com.wyrdly.post.application.dto.CreateCommentRequest;

/** Use case for creating a new comment on a post. */
public interface CreateCommentUseCase {

  /**
   * Persists a new comment authored by {@code userId} on the {@code postId} post.
   *
   * @param postId the target post id (from path param)
   * @param userId the authenticated user id (from JWT subject)
   * @param request validated inbound DTO
   * @return the persisted comment, enriched with authors and a generated id
   */
  CommentResponse createComment(String postId, String userId, CreateCommentRequest request);
}
