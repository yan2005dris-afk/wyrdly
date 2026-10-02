package com.wyrdly.post.application.usecase;

import com.wyrdly.post.domain.model.ReactionResult;
import com.wyrdly.post.domain.model.ReactionType;

/** Port-in use case: toggle a user's reaction on a post. */
public interface ReactToPostUseCase {

  /**
   * Toggles the {@code (userId, postId, type)} reaction.
   *
   * @throws com.wyrdly.post.domain.exception.PostNotFoundException if the post does not exist
   */
  ReactionResult react(String userId, String postId, ReactionType type);
}
