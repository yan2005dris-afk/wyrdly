package com.yaga.user.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yaga.user.application.dto.UpdateProfileRequest;
import com.yaga.user.application.dto.UserProfileResponse;
import com.yaga.user.domain.exception.UserProfileNotFoundException;
import com.yaga.user.domain.model.UserProfile;
import com.yaga.user.domain.repository.UserProfileRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserProfileServiceTest {

  private UserProfileRepository userProfileRepository;
  private UserProfileService userProfileService;

  @BeforeEach
  void setUp() {
    userProfileRepository = mock(UserProfileRepository.class);
    userProfileService = new UserProfileService(userProfileRepository);
  }

  @Test
  void getProfile_ReturnsProfile_WhenUserExists() {
    UserProfile profile =
        new UserProfile(
            "usr_123",
            "juanperez",
            "Juan Perez",
            "Bio",
            "http://avatar",
            42L,
            18L,
            true,
            Instant.parse("2026-09-24T18:30:00Z"));

    when(userProfileRepository.findProfileByUsername("juanperez", "usr_viewer"))
        .thenReturn(Optional.of(profile));

    UserProfileResponse response = userProfileService.getProfile("juanperez", "usr_viewer");

    assertEquals("usr_123", response.id());
    assertEquals("juanperez", response.username());
    assertEquals(42L, response.followersCount());
    assertEquals(18L, response.followingCount());
    assertEquals(true, response.isFollowing());
  }

  @Test
  void getProfile_ThrowsNotFound_WhenUserDoesNotExist() {
    when(userProfileRepository.findProfileByUsername("ghost", null)).thenReturn(Optional.empty());

    assertThrows(
        UserProfileNotFoundException.class, () -> userProfileService.getProfile("ghost", null));
  }

  @Test
  void updateProfile_ReturnsUpdatedProfile_WhenUserExists() {
    UpdateProfileRequest request =
        new UpdateProfileRequest("Juan Carlos Perez", "Nueva bio", "http://new-avatar");
    UserProfile updated =
        new UserProfile(
            "usr_123",
            "juanperez",
            "Juan Carlos Perez",
            "Nueva bio",
            "http://new-avatar",
            42L,
            18L,
            false,
            Instant.parse("2026-09-24T18:30:00Z"));

    when(userProfileRepository.updateProfile(
            "usr_123", "Juan Carlos Perez", "Nueva bio", "http://new-avatar"))
        .thenReturn(Optional.of(updated));

    UserProfileResponse response = userProfileService.updateProfile("usr_123", request);

    assertEquals("Juan Carlos Perez", response.fullName());
    assertEquals("Nueva bio", response.bio());
    assertEquals("http://new-avatar", response.avatarUrl());
    assertEquals(false, response.isFollowing());
    verify(userProfileRepository)
        .updateProfile("usr_123", "Juan Carlos Perez", "Nueva bio", "http://new-avatar");
  }

  @Test
  void updateProfile_ThrowsNotFound_WhenUserDoesNotExist() {
    UpdateProfileRequest request = new UpdateProfileRequest("Name", null, null);
    when(userProfileRepository.updateProfile("usr_ghost", "Name", null, null))
        .thenReturn(Optional.empty());

    assertThrows(
        UserProfileNotFoundException.class,
        () -> userProfileService.updateProfile("usr_ghost", request));
  }
}
