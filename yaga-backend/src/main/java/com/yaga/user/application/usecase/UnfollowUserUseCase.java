package com.yaga.user.application.usecase;

import com.yaga.user.application.dto.FollowActionResponse;

public interface UnfollowUserUseCase {
  FollowActionResponse unfollow(String userId, String targetUserId);
}
