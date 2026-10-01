package com.wyrdly.post.domain.repository;

import com.wyrdly.post.domain.model.FeedPost;
import com.wyrdly.post.domain.model.Post;
import java.util.List;
import java.util.Optional;

public interface PostRepository {
  Post save(Post post);

  Optional<Post> findById(String id);

  List<FeedPost> findFeedByUserId(String userId, int page, int pageSize);

  List<FeedPost> findFeedByUserIdWithCursor(
      String userId, java.time.Instant cursorCreatedAt, int limit);

  long countFeedByUserId(String userId);

  List<Post> findByAuthor(String authorId, int page, int pageSize);
}
