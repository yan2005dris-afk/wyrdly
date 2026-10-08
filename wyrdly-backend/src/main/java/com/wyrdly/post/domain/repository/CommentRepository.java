package com.wyrdly.post.domain.repository;

import com.wyrdly.post.domain.model.Comment;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and reading {@link Comment} entities. Concrete adapters live under
 * {@code post.infrastructure.persistence}. Returns {@link Optional#empty()} when a comment cannot
 * be resolved by id; infrastructure failures are propagated as runtime exceptions.
 */
public interface CommentRepository {

  /** Persists the supplied comment and returns the persisted domain object. */
  Comment save(Comment comment);

  /**
   * Returns the comments for a given post, ordered chronologically. Pagination is 1-based: {@code
   * page = 1} is the first page, {@code pageSize} is the maximum number of rows.
   */
  List<Comment> findByPostId(String postId, int page, int pageSize);

  /** Returns the total number of comments attached to {@code postId}. */
  long countByPostId(String postId);

  /** Looks up a single comment by id; returns {@link Optional#empty()} if none exists. */
  Optional<Comment> findById(String commentId);

  /** Removes the comment node and all its relationships. No-op if the id is unknown. */
  void deleteById(String commentId);
}
