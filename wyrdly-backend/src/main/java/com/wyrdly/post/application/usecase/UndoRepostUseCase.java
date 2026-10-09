package com.wyrdly.post.application.usecase;

import com.wyrdly.post.application.dto.RepostResponse;

public interface UndoRepostUseCase {

  /**
   * Removes any existing repost on the specified post by the authenticated user.
   *
   * @param userId identifier of the user unreposting the post
   * @param postId identifier of the post
   * @return {@link RepostResponse} containing the repost state and updated count
   */
  RepostResponse unrepost(String userId, String postId);
}
