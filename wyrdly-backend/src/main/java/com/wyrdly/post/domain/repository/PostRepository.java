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

  Optional<FeedPost> findFeedPostById(String postId, String userId);

  List<FeedPost> findFeedByUserId(String userId, int page, int pageSize);

  long countFeedByUserId(String userId);

  long countByAuthor(String authorId);

  /**
   * Profile timeline of {@code ownerId} (HU #150): posts the owner published ({@code :PUBLICA})
   * merged with posts the owner shared ({@code :COMPARTE}), ordered by the date of the owner's
   * action (publication or repost) descending, with a stable tie-break so pagination never
   * duplicates nor skips entries. Shared entries carry a {@link
   * com.wyrdly.post.domain.model.RepostContext}; a post the owner both published and shared appears
   * once, as a publication.
   *
   * @param viewerId optional; drives {@code userReaction} / {@code userHasReposted}
   * @param page 1-based page (values below 1 are treated as the first page)
   */
  List<FeedPost> findProfileTimeline(String ownerId, String viewerId, int page, int pageSize);

  /**
   * Toggles a user's reaction on a post. The {@code (:Usuario)-[:REACCIONA]->(:Post)} relationship
   * is created, removed, or updated atomically and a {@link ReactionResult} describes the outcome.
   *
   * @throws com.wyrdly.post.domain.exception.PostNotFoundException if the post does not exist
   */
  ReactionResult react(String userId, String postId, ReactionType type);
}
