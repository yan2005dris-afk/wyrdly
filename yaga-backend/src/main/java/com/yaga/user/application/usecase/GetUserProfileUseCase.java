package com.yaga.user.application.usecase;

import com.yaga.user.application.dto.UserProfileResponse;

public interface GetUserProfileUseCase {
  UserProfileResponse getProfile(String username, String viewerId);
}
