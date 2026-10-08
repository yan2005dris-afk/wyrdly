package com.wyrdly.post.application.usecase;

/** Use case for deleting a comment by id. */
public interface DeleteCommentUseCase {

  /**
   * Deletes a comment by id if the requesting user is authorized. Authorization allows either the
   * comment author or the post owner; everyone else gets a {@code
   * UnauthorizedCommentActionException}.
   *
   * @param postId post id from the request path (verified against the comment's postId)
   * @param commentId id of the comment to delete
   * @param userId authenticated user id (from JWT subject)
   */
  void deleteComment(String postId, String commentId, String userId);
}
