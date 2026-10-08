package com.wyrdly.post.domain.exception;

/**
 * Raised when the authenticated user attempts a write operation on a comment that they are not
 * authorized to perform — currently, deletion by someone other than the comment author or the post
 * owner. Mapped to {@code 403 Forbidden} by {@code PostExceptionMappers}.
 */
public class UnauthorizedCommentActionException extends RuntimeException {

  public UnauthorizedCommentActionException(String message) {
    super(message);
  }
}
