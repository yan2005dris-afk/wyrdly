package com.wyrdly.user.application.service;

import com.wyrdly.post.application.dto.PostResponse;
import com.wyrdly.post.domain.repository.PostRepository;
import com.wyrdly.user.application.dto.UpdateProfileRequest;
import com.wyrdly.user.application.dto.UserProfileResponse;
import com.wyrdly.user.application.usecase.GetUserProfileUseCase;
import com.wyrdly.user.application.usecase.UpdateUserProfileUseCase;
import com.wyrdly.user.domain.exception.UserProfileNotFoundException;
import com.wyrdly.user.domain.model.UserProfile;
import com.wyrdly.user.domain.repository.UserProfileRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Objects;

@ApplicationScoped
public class UserProfileService implements GetUserProfileUseCase, UpdateUserProfileUseCase {

  private final UserProfileRepository userProfileRepository;
  private final PostRepository postRepository;

  @Inject
  public UserProfileService(
      UserProfileRepository userProfileRepository, PostRepository postRepository) {
    this.userProfileRepository =
        Objects.requireNonNull(userProfileRepository, "userProfileRepository must not be null");
    this.postRepository =
        Objects.requireNonNull(postRepository, "postRepository must not be null");
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

  /**
   * Resolve {@code username} to the underlying user id and list the
   * posts that user has published, most recent first. Throws
   * {@link UserProfileNotFoundException} when the username does not
   * exist so the resource layer can return a clean 404.
   */
  public List<PostResponse> getUserPosts(String username, int page, int pageSize) {
    UserProfile profile =
        userProfileRepository
            .findProfileByUsername(username, null)
            .orElseThrow(
                () -> new UserProfileNotFoundException("El usuario '" + username + "' no existe."));

    return postRepository.findByAuthor(profile.id(), page, pageSize).stream()
        .map(
            post ->
                new PostResponse(
                    post.id(),
                    post.content(),
                    post.mediaUrl(),
                    post.createdAt(),
                    new PostResponse.AuthorDto(
                        profile.id(),
                        profile.username(),
                        profile.fullName(),
                        profile.avatarUrl())))
        .toList();
  }
}