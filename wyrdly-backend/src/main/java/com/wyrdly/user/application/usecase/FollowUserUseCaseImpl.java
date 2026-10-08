package com.wyrdly.user.application.usecase;

import com.wyrdly.user.application.dto.FollowActionResponse;
import com.wyrdly.user.domain.event.UserFollowRelationshipChangedEvent;
import com.wyrdly.user.domain.exception.SelfFollowNotAllowedException;
import com.wyrdly.user.domain.repository.UserProfileRepository;
import com.wyrdly.user.infrastructure.qualifier.ResilientNeo4j;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import java.util.Objects;

@ApplicationScoped
public class FollowUserUseCaseImpl implements FollowUserUseCase {

  private final UserProfileRepository repository;
  private final Event<UserFollowRelationshipChangedEvent> followEvent;

  @Inject
  public FollowUserUseCaseImpl(
      @ResilientNeo4j UserProfileRepository repository,
      Event<UserFollowRelationshipChangedEvent> followEvent) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.followEvent = followEvent;
  }

  @Override
  public FollowActionResponse follow(String userId, String targetUserId) {
    if (userId.equals(targetUserId)) {
      throw new SelfFollowNotAllowedException(userId);
    }

    repository.validateUserExists(targetUserId);
    boolean created = repository.followUser(userId, targetUserId);

    // Only a real state change is announced: a repeated follow (stale UI, second tab, retry)
    // must not push another "new follower" notification.
    if (created && followEvent != null) {
      followEvent.fire(new UserFollowRelationshipChangedEvent(userId, targetUserId, true));
    }

    return new FollowActionResponse("Usuario seguido exitosamente.", targetUserId, true);
  }
}
