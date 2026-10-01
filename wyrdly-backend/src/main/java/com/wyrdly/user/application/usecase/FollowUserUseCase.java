package com.wyrdly.user.application.usecase;

import com.wyrdly.user.application.dto.FollowActionResponse;

public interface FollowUserUseCase {
  FollowActionResponse follow(String userId, String targetUserId);
}
