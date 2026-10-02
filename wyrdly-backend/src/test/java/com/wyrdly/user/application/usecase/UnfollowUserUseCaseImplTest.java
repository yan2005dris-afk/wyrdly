package com.wyrdly.user.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.wyrdly.user.application.dto.FollowActionResponse;
import com.wyrdly.user.domain.event.UserFollowRelationshipChangedEvent;
import com.wyrdly.user.domain.exception.UserProfileNotFoundException;
import com.wyrdly.user.domain.repository.UserProfileRepository;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UnfollowUserUseCaseImplTest {

  private UserProfileRepository userProfileRepository;
  private Event<UserFollowRelationshipChangedEvent> followEvent;
  private UnfollowUserUseCase unfollowUserUseCase;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    userProfileRepository = mock(UserProfileRepository.class);
    followEvent = mock(Event.class);
    unfollowUserUseCase = new UnfollowUserUseCaseImpl(userProfileRepository, followEvent);
  }

  @Test
  void unfollow_DeletesRelationship_WhenTargetUserExists() {
    String userId = "usr_123";
    String targetUserId = "usr_456";

    FollowActionResponse response = unfollowUserUseCase.unfollow(userId, targetUserId);

    assertEquals("Se dejó de seguir al usuario.", response.message());
    assertEquals(targetUserId, response.targetUserId());
    assertEquals(false, response.following());
    verify(userProfileRepository).validateUserExists(targetUserId);
    verify(userProfileRepository).unfollowUser(userId, targetUserId);
    verify(followEvent).fire(new UserFollowRelationshipChangedEvent(userId, targetUserId, false));
  }

  @Test
  void unfollow_ThrowsUserNotFound_WhenTargetUserDoesNotExist() {
    String userId = "usr_123";
    String targetUserId = "usr_ghost";

    org.mockito.Mockito.doThrow(new UserProfileNotFoundException("User not found"))
        .when(userProfileRepository)
        .validateUserExists(targetUserId);

    assertThrows(
        UserProfileNotFoundException.class,
        () -> unfollowUserUseCase.unfollow(userId, targetUserId));

    verify(userProfileRepository).validateUserExists(targetUserId);
  }
}
