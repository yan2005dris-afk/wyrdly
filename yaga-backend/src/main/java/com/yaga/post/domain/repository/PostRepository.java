package com.yaga.post.domain.repository;

import com.yaga.post.domain.model.Post;
import java.util.Optional;

public interface PostRepository {
  Post save(Post post);

  Optional<Post> findById(String id);
}
