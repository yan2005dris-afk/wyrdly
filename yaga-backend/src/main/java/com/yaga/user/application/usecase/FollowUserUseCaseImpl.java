package com.yaga.user.application.usecase;

import com.yaga.chat.application.port.FollowValidationPort;
import com.yaga.user.application.dto.FollowActionResponse;
import com.yaga.user.domain.exception.SelfFollowNotAllowedException;
import com.yaga.user.domain.repository.UserProfileRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Objects;

@ApplicationScoped
public class FollowUserUseCaseImpl implements FollowUserUseCase {

  private final UserProfileRepository repository;
  private FollowValidationPort followValidationPort;

  @Inject
  public FollowUserUseCaseImpl(
      UserProfileRepository repository, FollowValidationPort followValidationPort) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.followValidationPort =
        Objects.requireNonNull(followValidationPort, "followValidationPort must not be null");
  }

  @Override
  public FollowActionResponse follow(String userId, String targetUserId) {
    if (userId.equals(targetUserId)) {
      throw new SelfFollowNotAllowedException(userId);
    }

    repository.validateUserExists(targetUserId);
    repository.followUser(userId, targetUserId);

    // ✅ Invalidar cache cuando cambia relación follow
    followValidationPort.invalidateCache(userId, targetUserId);

    return new FollowActionResponse("Usuario seguido exitosamente.", targetUserId, true);
  }
}
