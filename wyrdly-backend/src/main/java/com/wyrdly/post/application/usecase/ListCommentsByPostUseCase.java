package com.wyrdly.post.application.usecase;

import com.wyrdly.post.application.dto.CommentListResponseDto;

/** Use case for listing the comments attached to a given post. */
public interface ListCommentsByPostUseCase {

  /**
   * Returns a paginated, chronological list of comments for {@code postId}. The post must exist —
   * otherwise a {@code PostNotFoundException} is raised upstream.
   *
   * @param postId target post id
   * @param page 1-based page index
   * @param pageSize maximum number of rows per page
   */
  CommentListResponseDto getComments(String postId, int page, int pageSize);
}
