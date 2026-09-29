package com.wyrdly.post.application.usecase;

import com.wyrdly.post.application.dto.CreatePostRequest;
import com.wyrdly.post.application.dto.PostResponse;

public interface CreatePostUseCase {
  PostResponse createPost(String userId, CreatePostRequest request);
}
