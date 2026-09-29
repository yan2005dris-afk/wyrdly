package com.yaga.post.application.usecase;

import com.yaga.post.application.dto.CreatePostRequest;
import com.yaga.post.application.dto.PostResponse;

public interface CreatePostUseCase {
  PostResponse createPost(String userId, CreatePostRequest request);
}
