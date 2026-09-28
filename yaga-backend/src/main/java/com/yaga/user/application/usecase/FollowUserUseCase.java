package com.yaga.user.application.usecase;

import com.yaga.user.application.dto.FollowActionResponse;

public interface FollowUserUseCase {
  FollowActionResponse follow(String userId, String targetUserId);
}
