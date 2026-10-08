package com.wyrdly.user.application.usecase;

import com.wyrdly.user.application.dto.FollowActionResponse;
import com.wyrdly.user.domain.event.UserFollowRelationshipChangedEvent;
import com.wyrdly.user.domain.repository.UserProfileRepository;
import com.wyrdly.user.infrastructure.qualifier.ResilientNeo4j;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import java.util.Objects;

@ApplicationScoped
public class UnfollowUserUseCaseImpl implements UnfollowUserUseCase {

  private final UserProfileRepository repository;
  private final Event<UserFollowRelationshipChangedEvent> followEvent;

  @Inject
  public UnfollowUserUseCaseImpl(
      @ResilientNeo4j UserProfileRepository repository,
      Event<UserFollowRelationshipChangedEvent> followEvent) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.followEvent = followEvent;
  }

  @Override
  public FollowActionResponse unfollow(String userId, String targetUserId) {
    repository.validateUserExists(targetUserId);
    boolean removed = repository.unfollowUser(userId, targetUserId);

    if (removed && followEvent != null) {
      followEvent.fire(new UserFollowRelationshipChangedEvent(userId, targetUserId, false));
    }

    return new FollowActionResponse("Se dejó de seguir al usuario.", targetUserId, false);
  }
}
