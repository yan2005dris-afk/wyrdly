package com.wyrdly.post.domain.exception;

/**
 * Raised when a comment id cannot be resolved, either because no such {@code (:Comentario)} exists
 * or because it does not belong to the {@code postId} in the request path. The unified "not found"
 * response intentionally does not distinguish between "missing" and "belongs to another post" so
 * that an attacker cannot enumerate comment ids by observing divergent error codes.
 *
 * <p>Mapped to {@code 404 Not Found} by {@code PostExceptionMappers}.
 */
public class CommentNotFoundException extends RuntimeException {

  private final String commentId;

  public CommentNotFoundException(String commentId) {
    super("Comment not found: " + commentId);
    this.commentId = commentId;
  }

  public CommentNotFoundException(String commentId, String postId) {
    super("Comment not found: " + commentId + " for post " + postId);
    this.commentId = commentId;
  }

  public String commentId() {
    return commentId;
  }
}
