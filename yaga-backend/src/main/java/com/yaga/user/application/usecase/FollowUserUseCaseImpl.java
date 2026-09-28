package com.yaga.user.application.usecase;

import com.yaga.user.application.dto.FollowActionResponse;
import com.yaga.user.domain.exception.SelfFollowNotAllowedException;
import com.yaga.user.domain.repository.UserProfileRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Objects;

@ApplicationScoped
public class FollowUserUseCaseImpl implements FollowUserUseCase {

  private final UserProfileRepository repository;

  @Inject
  public FollowUserUseCaseImpl(UserProfileRepository repository) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
  }

  @Override
  public FollowActionResponse follow(String userId, String targetUserId) {
    if (userId.equals(targetUserId)) {
      throw new SelfFollowNotAllowedException(userId);
    }

    repository.validateUserExists(targetUserId);
    repository.followUser(userId, targetUserId);

    return new FollowActionResponse(
        "Usuario seguido exitosamente.", targetUserId, true);
  }
}
