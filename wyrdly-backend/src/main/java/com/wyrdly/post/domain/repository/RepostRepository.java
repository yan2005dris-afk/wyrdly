package com.wyrdly.post.domain.repository;

import com.wyrdly.post.domain.exception.PostNotFoundException;
import com.wyrdly.post.domain.model.RepostResult;

/** Domain port for repost / boost persistence operations. */
public interface RepostRepository {

  /**
   * Reposts the specified post on behalf of {@code userId}.
   *
   * @param userId identifier of the user reposting the post
   * @param postId identifier of the target post
   * @param requestId unique identifier used for idempotency and detecting whether the relationship
   *     was created
   * @return {@link RepostResult} indicating the current status, whether a state change occurred,
   *     total reposts, and the post author ID
   * @throws PostNotFoundException if the target post does not exist
   */
  RepostResult repost(String userId, String postId, String requestId);

  /**
   * Removes any existing repost on the specified post by {@code userId}.
   *
   * @param userId identifier of the user unreposting the post
   * @param postId identifier of the target post
   * @return {@link RepostResult} with {@code reposted = false}, whether a state change occurred,
   *     total reposts, and the post author ID
   * @throws PostNotFoundException if the target post does not exist
   */
  RepostResult unrepost(String userId, String postId);
}
