package com.wyrdly.user.application.usecase;

import com.wyrdly.user.application.dto.UserProfileResponse;

public interface GetUserProfileUseCase {
  UserProfileResponse getProfile(String username, String viewerId);
}
