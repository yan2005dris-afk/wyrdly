package com.wyrdly.user.application.usecase;

import com.wyrdly.chat.application.port.FollowValidationPort;

import com.wyrdly.user.infrastructure.qualifier.ResilientNeo4j;

import com.wyrdly.user.application.dto.FollowActionResponse;
import com.wyrdly.user.domain.repository.UserProfileRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Objects;

@ApplicationScoped
public class UnfollowUserUseCaseImpl implements UnfollowUserUseCase {

  private final UserProfileRepository repository;
  private final FollowValidationPort followValidationPort;

  @Inject
  public UnfollowUserUseCaseImpl(
      @ResilientNeo4j UserProfileRepository repository, FollowValidationPort followValidationPort) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.followValidationPort =
        Objects.requireNonNull(followValidationPort, "followValidationPort must not be null");
  }

  @Override
  public FollowActionResponse unfollow(String userId, String targetUserId) {
    repository.validateUserExists(targetUserId);
    repository.unfollowUser(userId, targetUserId);

    // ✅ Invalidar cache cuando cambia relación follow
    followValidationPort.invalidateCache(userId, targetUserId);

    return new FollowActionResponse("Se dejó de seguir al usuario.", targetUserId, false);
  }
}
