package com.yaga.post.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yaga.auth.domain.model.User;
import com.yaga.auth.domain.repository.UserRepository;
import com.yaga.post.application.dto.CreatePostRequest;
import com.yaga.post.application.dto.PostResponse;
import com.yaga.post.domain.exception.PostValidationException;
import com.yaga.post.domain.model.Post;
import com.yaga.post.domain.repository.PostRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PostServiceTest {

  private PostRepository postRepository;
  private UserRepository userRepository;
  private PostService postService;

  @BeforeEach
  void setUp() {
    postRepository = mock(PostRepository.class);
    userRepository = mock(UserRepository.class);
    postService = new PostService(postRepository, userRepository);
  }

  @Test
  void createPost_Success_WithValidContent() {
    String userId = "usr_123";
    CreatePostRequest request = new CreatePostRequest("This is a test post", null);
    User author =
        new User(
            userId,
            "testuser",
            "test@yaga.social",
            "hashed_pass",
            "Test User",
            "Bio test",
            "http://avatar.jpg",
            Instant.now());

    when(postRepository.save(any(Post.class)))
        .thenAnswer(
            invocation -> {
              Post post = invocation.getArgument(0);
              return post;
            });
    when(userRepository.findById(userId)).thenReturn(Optional.of(author));

    PostResponse response = postService.createPost(userId, request);

    assertNotNull(response);
    assertEquals("This is a test post", response.content());
    assertEquals(userId, response.author().id());
    assertEquals("testuser", response.author().username());
    assertEquals("Test User", response.author().fullName());
    assertEquals(0, response.reactionCounts().get("LIKE"));

    verify(postRepository).save(any(Post.class));
    verify(userRepository).findById(userId);
  }

  @Test
  void createPost_Success_WithMediaUrl() {
    String userId = "usr_123";
    String mediaUrl = "https://example.com/image.jpg";
    CreatePostRequest request = new CreatePostRequest("Post with media", mediaUrl);
    User author =
        new User(
            userId, "testuser", "test@yaga.social", "hashed", "Test User", "", "", Instant.now());

    when(postRepository.save(any(Post.class)))
        .thenAnswer(
            invocation -> {
              Post post = invocation.getArgument(0);
              return post;
            });
    when(userRepository.findById(userId)).thenReturn(Optional.of(author));

    PostResponse response = postService.createPost(userId, request);

    assertEquals(mediaUrl, response.mediaUrl());
    verify(postRepository).save(any(Post.class));
  }

  @Test
  void createPost_ThrowsPostValidationException_WhenContentIsNull() {
    String userId = "usr_123";
    CreatePostRequest request = new CreatePostRequest(null, null);

    PostValidationException exception =
        assertThrows(PostValidationException.class, () -> postService.createPost(userId, request));

    assertEquals("Content is required", exception.getMessage());
  }

  @Test
  void createPost_ThrowsPostValidationException_WhenContentIsBlank() {
    String userId = "usr_123";
    CreatePostRequest request = new CreatePostRequest("   ", null);

    PostValidationException exception =
        assertThrows(PostValidationException.class, () -> postService.createPost(userId, request));

    assertEquals("Content is required", exception.getMessage());
  }

  @Test
  void createPost_ThrowsPostValidationException_WhenContentExceedsMaxLength() {
    String userId = "usr_123";
    String tooLongContent = "a".repeat(1001);
    CreatePostRequest request = new CreatePostRequest(tooLongContent, null);

    PostValidationException exception =
        assertThrows(PostValidationException.class, () -> postService.createPost(userId, request));

    assertEquals("Content must not exceed 1000 characters", exception.getMessage());
  }

  @Test
  void createPost_ThrowsPostValidationException_WhenMediaUrlIsInvalid() {
    String userId = "usr_123";
    CreatePostRequest request = new CreatePostRequest("Valid content", "not-a-valid-url");

    PostValidationException exception =
        assertThrows(PostValidationException.class, () -> postService.createPost(userId, request));

    assertEquals("Invalid media URL format", exception.getMessage());
  }

  @Test
  void createPost_Success_WithValidHttpsUrl() {
    String userId = "usr_123";
    CreatePostRequest request = new CreatePostRequest("Content", "https://example.com/image.jpg");
    User author =
        new User(
            userId, "testuser", "test@yaga.social", "hashed", "Test User", "", "", Instant.now());

    when(postRepository.save(any(Post.class)))
        .thenAnswer(
            invocation -> {
              Post post = invocation.getArgument(0);
              return post;
            });
    when(userRepository.findById(userId)).thenReturn(Optional.of(author));

    PostResponse response = postService.createPost(userId, request);

    assertEquals("https://example.com/image.jpg", response.mediaUrl());
  }

  @Test
  void createPost_Success_WithValidHttpUrl() {
    String userId = "usr_123";
    CreatePostRequest request = new CreatePostRequest("Content", "http://example.com/image.jpg");
    User author =
        new User(
            userId, "testuser", "test@yaga.social", "hashed", "Test User", "", "", Instant.now());

    when(postRepository.save(any(Post.class)))
        .thenAnswer(
            invocation -> {
              Post post = invocation.getArgument(0);
              return post;
            });
    when(userRepository.findById(userId)).thenReturn(Optional.of(author));

    PostResponse response = postService.createPost(userId, request);

    assertEquals("http://example.com/image.jpg", response.mediaUrl());
  }

  @Test
  void createPost_ThrowsPostValidationException_WhenAuthorUserNotFound() {
    String userId = "usr_nonexistent";
    CreatePostRequest request = new CreatePostRequest("Valid content", null);

    when(postRepository.save(any(Post.class)))
        .thenAnswer(
            invocation -> {
              Post post = invocation.getArgument(0);
              return post;
            });
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    PostValidationException exception =
        assertThrows(PostValidationException.class, () -> postService.createPost(userId, request));

    assertEquals("Author user not found", exception.getMessage());
  }

  @Test
  void createPost_GeneratesUniquePostIds() {
    String userId = "usr_123";
    CreatePostRequest request = new CreatePostRequest("Content", null);
    User author =
        new User(
            userId, "testuser", "test@yaga.social", "hashed", "Test User", "", "", Instant.now());

    when(postRepository.save(any(Post.class)))
        .thenAnswer(
            invocation -> {
              Post post = invocation.getArgument(0);
              return post;
            });
    when(userRepository.findById(userId)).thenReturn(Optional.of(author));

    PostResponse response1 = postService.createPost(userId, request);
    PostResponse response2 = postService.createPost(userId, request);

    assertNotNull(response1.id());
    assertNotNull(response2.id());
    assertEquals(false, response1.id().equals(response2.id()));
  }

  @Test
  void createPost_EnrichesAuthorInformation() {
    String userId = "usr_123";
    CreatePostRequest request = new CreatePostRequest("Content", null);
    User author =
        new User(
            userId,
            "johndoe",
            "john@yaga.social",
            "hashed",
            "John Doe",
            "My bio",
            "https://avatar.jpg",
            Instant.now());

    when(postRepository.save(any(Post.class)))
        .thenAnswer(
            invocation -> {
              Post post = invocation.getArgument(0);
              return post;
            });
    when(userRepository.findById(userId)).thenReturn(Optional.of(author));

    PostResponse response = postService.createPost(userId, request);

    assertEquals("johndoe", response.author().username());
    assertEquals("John Doe", response.author().fullName());
    assertEquals("https://avatar.jpg", response.author().avatarUrl());
  }
}
