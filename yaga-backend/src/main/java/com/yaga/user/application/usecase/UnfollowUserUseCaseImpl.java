package com.yaga.user.application.usecase;

import com.yaga.chat.application.port.FollowValidationPort;
import com.yaga.user.application.dto.FollowActionResponse;
import com.yaga.user.domain.repository.UserProfileRepository;
import com.yaga.user.infrastructure.qualifier.ResilientNeo4j;
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
