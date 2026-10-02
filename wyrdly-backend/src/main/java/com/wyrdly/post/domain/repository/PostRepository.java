package com.wyrdly.post.domain.repository;

import com.wyrdly.post.domain.model.FeedPost;
import com.wyrdly.post.domain.model.Post;
import com.wyrdly.post.domain.model.ReactionResult;
import com.wyrdly.post.domain.model.ReactionType;
import java.util.List;
import java.util.Optional;

/**
 * Port for persisting posts and reading them back from storage. Concrete adapters live under {@code
 * infrastructure/persistence}.
 */
public interface PostRepository {
  Post save(Post post);

  Optional<Post> findById(String id);

  List<FeedPost> findFeedByUserId(String userId, int page, int pageSize);

  long countFeedByUserId(String userId);

  List<Post> findByAuthor(String authorId, int page, int pageSize);

  long countByAuthor(String authorId);

  /**
   * Toggles a user's reaction on a post. The {@code (:Usuario)-[:REACCIONA]->(:Post)} relationship
   * is created, removed, or updated atomically and a {@link ReactionResult} describes the outcome.
   *
   * @throws com.wyrdly.post.domain.exception.PostNotFoundException if the post does not exist
   */
  ReactionResult react(String userId, String postId, ReactionType type);
}
