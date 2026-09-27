package com.yaga.user.application.service;

import com.yaga.user.application.dto.UpdateProfileRequest;
import com.yaga.user.application.dto.UserProfileResponse;
import com.yaga.user.application.usecase.GetUserProfileUseCase;
import com.yaga.user.application.usecase.UpdateUserProfileUseCase;
import com.yaga.user.domain.exception.UserProfileNotFoundException;
import com.yaga.user.domain.model.UserProfile;
import com.yaga.user.domain.repository.UserProfileRepository;
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
