package com.wyrdly.post.application.usecase;

import com.wyrdly.post.application.dto.RepostResponse;

public interface RepostPostUseCase {

  /**
   * Reposts the specified post for the authenticated user.
   *
   * @param userId identifier of the user reposting the post
   * @param postId identifier of the post
   * @return {@link RepostResponse} containing the repost state and updated count
   */
  RepostResponse repost(String userId, String postId);
}
