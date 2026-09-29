package com.wyrdly.user.application.usecase;

import com.wyrdly.user.application.dto.UpdateProfileRequest;
import com.wyrdly.user.application.dto.UserProfileResponse;

public interface UpdateUserProfileUseCase {
  UserProfileResponse updateProfile(String userId, UpdateProfileRequest request);
}
