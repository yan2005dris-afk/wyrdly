package com.wyrdly.user.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.wyrdly.chat.application.port.FollowValidationPort;

import com.wyrdly.user.application.dto.FollowActionResponse;
import com.wyrdly.user.domain.exception.UserProfileNotFoundException;
import com.wyrdly.user.domain.repository.UserProfileRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UnfollowUserUseCaseImplTest {

  private UserProfileRepository userProfileRepository;
  private FollowValidationPort followValidationPort;
  private UnfollowUserUseCase unfollowUserUseCase;

  @BeforeEach
  void setUp() {
    userProfileRepository = mock(UserProfileRepository.class);
    followValidationPort = mock(FollowValidationPort.class);
    unfollowUserUseCase = new UnfollowUserUseCaseImpl(userProfileRepository, followValidationPort);
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
