package com.wyrdly.post.domain.repository;

import com.wyrdly.post.domain.model.Post;
import java.util.Optional;

public interface PostRepository {
  Post save(Post post);

  Optional<Post> findById(String id);
}
