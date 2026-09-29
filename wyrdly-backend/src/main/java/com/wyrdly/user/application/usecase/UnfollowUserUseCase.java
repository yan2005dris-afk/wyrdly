package com.wyrdly.user.application.usecase;

import com.wyrdly.user.application.dto.FollowActionResponse;

public interface UnfollowUserUseCase {
  FollowActionResponse unfollow(String userId, String targetUserId);
}
