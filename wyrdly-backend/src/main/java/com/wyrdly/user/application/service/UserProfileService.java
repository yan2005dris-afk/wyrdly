package com.wyrdly.user.application.service;

import com.wyrdly.user.application.dto.UpdateProfileRequest;
import com.wyrdly.user.application.dto.UserProfileResponse;
import com.wyrdly.user.application.usecase.GetUserProfileUseCase;
import com.wyrdly.user.application.usecase.UpdateUserProfileUseCase;
import com.wyrdly.user.domain.exception.UserProfileNotFoundException;
import com.wyrdly.user.domain.model.UserProfile;
import com.wyrdly.user.domain.repository.UserProfileRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Objects;

@ApplicationScoped
public class UserProfileService implements GetUserProfileUseCase, UpdateUserProfileUseCase {

  private final UserProfileRepository userProfileRepository;

  @Inject
  public UserProfileService(UserProfileRepository userProfileRepository) {
    this.userProfileRepository =
        Objects.requireNonNull(userProfileRepository, "userProfileRepository must not be null");
  }

  @Override
  public UserProfileResponse getProfile(String username, String viewerId) {
    UserProfile profile =
        userProfileRepository
            .findProfileByUsername(username, viewerId)
            .orElseThrow(
                () -> new UserProfileNotFoundException("El usuario '" + username + "' no existe."));
    return UserProfileResponse.fromDomain(profile);
  }

  @Override
  public UserProfileResponse updateProfile(String userId, UpdateProfileRequest request) {
    UserProfile profile =
        userProfileRepository
            .updateProfile(userId, request.fullName(), request.bio(), request.avatarUrl())
            .orElseThrow(
                () -> new UserProfileNotFoundException("Usuario no encontrado: " + userId));
    return UserProfileResponse.fromDomain(profile);
  }
}
