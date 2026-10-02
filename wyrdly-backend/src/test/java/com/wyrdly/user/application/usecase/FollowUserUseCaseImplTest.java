package com.wyrdly.user.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.wyrdly.user.application.dto.FollowActionResponse;
import com.wyrdly.user.domain.event.UserFollowRelationshipChangedEvent;
import com.wyrdly.user.domain.exception.SelfFollowNotAllowedException;
import com.wyrdly.user.domain.exception.UserProfileNotFoundException;
import com.wyrdly.user.domain.repository.UserProfileRepository;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FollowUserUseCaseImplTest {

  private UserProfileRepository userProfileRepository;
  private Event<UserFollowRelationshipChangedEvent> followEvent;
  private FollowUserUseCase followUserUseCase;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    userProfileRepository = mock(UserProfileRepository.class);
    followEvent = mock(Event.class);
    followUserUseCase = new FollowUserUseCaseImpl(userProfileRepository, followEvent);
  }

  @Test
  void follow_CreatesRelationship_WhenTargetUserExists() {
    String userId = "usr_123";
    String targetUserId = "usr_456";

    FollowActionResponse response = followUserUseCase.follow(userId, targetUserId);

    assertEquals("Usuario seguido exitosamente.", response.message());
    assertEquals(targetUserId, response.targetUserId());
    assertEquals(true, response.following());
    verify(userProfileRepository).validateUserExists(targetUserId);
    verify(userProfileRepository).followUser(userId, targetUserId);
    verify(followEvent).fire(new UserFollowRelationshipChangedEvent(userId, targetUserId, true));
  }

  @Test
  void follow_ThrowsSelfFollowNotAllowed_WhenUserFollowsThemself() {
    String userId = "usr_123";

    assertThrows(
        SelfFollowNotAllowedException.class, () -> followUserUseCase.follow(userId, userId));

    verifyNoInteractions(userProfileRepository);
  }

  @Test
  void follow_ThrowsUserNotFound_WhenTargetUserDoesNotExist() {
    String userId = "usr_123";
    String targetUserId = "usr_ghost";

    org.mockito.Mockito.doThrow(new UserProfileNotFoundException("User not found"))
        .when(userProfileRepository)
        .validateUserExists(targetUserId);

    assertThrows(
        UserProfileNotFoundException.class, () -> followUserUseCase.follow(userId, targetUserId));

    verify(userProfileRepository).validateUserExists(targetUserId);
  }
}
