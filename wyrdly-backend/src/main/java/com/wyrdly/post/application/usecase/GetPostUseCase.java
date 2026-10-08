package com.wyrdly.post.application.usecase;

import com.wyrdly.post.application.dto.PostResponse;

public interface GetPostUseCase {
  PostResponse getPost(String userId, String postId);
}
