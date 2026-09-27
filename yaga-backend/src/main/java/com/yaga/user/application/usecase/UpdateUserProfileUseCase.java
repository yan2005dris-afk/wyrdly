package com.yaga.user.application.usecase;

import com.yaga.user.application.dto.UpdateProfileRequest;
import com.yaga.user.application.dto.UserProfileResponse;

public interface UpdateUserProfileUseCase {
  UserProfileResponse updateProfile(String userId, UpdateProfileRequest request);
}
