package com.wyrdly.user.application.usecase;

import com.wyrdly.user.application.dto.FollowActionResponse;
import com.wyrdly.user.domain.repository.UserProfileRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Objects;

@ApplicationScoped
public class UnfollowUserUseCaseImpl implements UnfollowUserUseCase {

  private final UserProfileRepository repository;

  @Inject
  public UnfollowUserUseCaseImpl(UserProfileRepository repository) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
  }

  @Override
  public FollowActionResponse unfollow(String userId, String targetUserId) {
    repository.validateUserExists(targetUserId);
    repository.unfollowUser(userId, targetUserId);

    return new FollowActionResponse("Se dejó de seguir al usuario.", targetUserId, false);
  }
}
