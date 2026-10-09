package com.wyrdly.user.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wyrdly.post.application.dto.PostResponse;
import com.wyrdly.post.application.usecase.GetProfileTimelineUseCase;
import com.wyrdly.user.application.dto.UpdateProfileRequest;
import com.wyrdly.user.application.dto.UserProfileResponse;
import com.wyrdly.user.domain.exception.UserProfileNotFoundException;
import com.wyrdly.user.domain.model.UserProfile;
import com.wyrdly.user.domain.repository.UserProfileRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserProfileServiceTest {

  private UserProfileRepository userProfileRepository;
  private GetProfileTimelineUseCase getProfileTimelineUseCase;
  private UserProfileService userProfileService;

  @BeforeEach
  void setUp() {
    userProfileRepository = mock(UserProfileRepository.class);
    getProfileTimelineUseCase = mock(GetProfileTimelineUseCase.class);
    userProfileService = new UserProfileService(userProfileRepository, getProfileTimelineUseCase);
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
            5L,
            true,
            Instant.parse("2026-09-24T18:30:00Z"));

    when(userProfileRepository.findProfileByUsername("juanperez", "usr_viewer"))
        .thenReturn(Optional.of(profile));

    UserProfileResponse response = userProfileService.getProfile("juanperez", "usr_viewer");

    assertEquals("usr_123", response.id());
    assertEquals("juanperez", response.username());
    assertEquals(42L, response.followersCount());
    assertEquals(18L, response.followingCount());
    assertEquals(5L, response.postsCount());
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
            5L,
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

  @Test
  void getUserPosts_DelegatesToProfileTimelineUseCase_WithResolvedOwnerId() {
    when(userProfileRepository.findProfileByUsername("juanperez", "usr_viewer"))
        .thenReturn(Optional.of(profile()));
    PostResponse post =
        new PostResponse(
            "post_1",
            "Hola mundo",
            null,
            Instant.parse("2026-09-24T19:00:00Z"),
            new PostResponse.AuthorDto("usr_123", "juanperez", "Juan Perez", "http://avatar"),
            new PostResponse.ReactionCounts(5L, 2L, 1L),
            7L,
            3L,
            "LOVE",
            true);
    when(getProfileTimelineUseCase.getProfileTimeline("usr_123", "usr_viewer", 1, 20))
        .thenReturn(List.of(post));

    List<PostResponse> posts = userProfileService.getUserPosts("juanperez", "usr_viewer", 1, 20);

    assertEquals(List.of(post), posts);
    verify(getProfileTimelineUseCase).getProfileTimeline("usr_123", "usr_viewer", 1, 20);
  }

  @Test
  void getUserPosts_ReturnsRepostsWithOriginalAuthorAndRepostContext() {
    when(userProfileRepository.findProfileByUsername("juanperez", null))
        .thenReturn(Optional.of(profile()));
    Instant repostedAt = Instant.parse("2026-09-25T10:00:00Z");
    PostResponse repost =
        new PostResponse(
            "post_alice",
            "Post de Alice",
            null,
            Instant.parse("2026-09-20T10:00:00Z"),
            new PostResponse.AuthorDto("usr_alice", "alice", "Alice Doe", null),
            new PostResponse.ReactionCounts(0L, 0L, 0L),
            0L,
            1L,
            null,
            false,
            new PostResponse.RepostContextDto(
                "usr_123", "juanperez", "Juan Perez", "http://avatar", repostedAt));
    when(getProfileTimelineUseCase.getProfileTimeline("usr_123", null, 1, 20))
        .thenReturn(List.of(repost));

    List<PostResponse> posts = userProfileService.getUserPosts("juanperez", 1, 20);

    assertEquals(1, posts.size());
    assertEquals("usr_alice", posts.get(0).author().id());
    assertEquals("juanperez", posts.get(0).repostContext().reposterUsername());
    assertEquals(repostedAt, posts.get(0).repostContext().repostedAt());
  }

  @Test
  void getUserPosts_ThrowsNotFound_AndSkipsTimeline_WhenUserDoesNotExist() {
    when(userProfileRepository.findProfileByUsername("ghost", null)).thenReturn(Optional.empty());

    assertThrows(
        UserProfileNotFoundException.class,
        () -> userProfileService.getUserPosts("ghost", null, 1, 20));
    verifyNoInteractions(getProfileTimelineUseCase);
  }

  private static UserProfile profile() {
    return new UserProfile(
        "usr_123",
        "juanperez",
        "Juan Perez",
        "Bio",
        "http://avatar",
        42L,
        18L,
        5L,
        true,
        Instant.parse("2026-09-24T18:30:00Z"));
  }
}
