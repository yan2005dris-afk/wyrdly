package com.wyrdly.post.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wyrdly.auth.domain.model.User;
import com.wyrdly.auth.domain.repository.UserRepository;
import com.wyrdly.post.application.dto.CreatePostRequest;
import com.wyrdly.post.application.dto.PostResponse;
import com.wyrdly.post.domain.exception.PostValidationException;
import com.wyrdly.post.domain.model.Post;
import com.wyrdly.post.domain.repository.PostRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PostService}.
 *
 * <p>Input validation (content/mediaUrl format, length, required fields) is now declarative on
 * {@link CreatePostRequest} and exercised at the REST boundary in {@code PostResourceTest}. This
 * suite focuses on service-level business logic: ID generation, author enrichment, persistence
 * orchestration, and business exceptions.
 */
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

  private User sampleAuthor(String userId) {
    return new User(
        userId, "testuser", "test@yaga.social", "hashed", "Test User", "", "", Instant.now());
  }

  @Test
  void createPost_Success_WithValidContent() {
    String userId = "usr_123";
    CreatePostRequest request = new CreatePostRequest("This is a test post", null);

    when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(userRepository.findById(userId)).thenReturn(Optional.of(sampleAuthor(userId)));

    PostResponse response = postService.createPost(userId, request);

    assertNotNull(response);
    assertEquals("This is a test post", response.content());
    assertEquals(userId, response.author().id());
    assertEquals(0, response.reactionCounts().get("LIKE"));

    verify(postRepository).save(any(Post.class));
    verify(userRepository).findById(userId);
  }

  @Test
  void createPost_Success_WithMediaUrl() {
    String userId = "usr_123";
    String mediaUrl = "https://example.com/image.jpg";
    CreatePostRequest request = new CreatePostRequest("Post with media", mediaUrl);

    when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(userRepository.findById(userId)).thenReturn(Optional.of(sampleAuthor(userId)));

    PostResponse response = postService.createPost(userId, request);

    assertEquals(mediaUrl, response.mediaUrl());
    verify(postRepository).save(any(Post.class));
  }

  @Test
  void createPost_Success_WithValidHttpsUrl() {
    String userId = "usr_123";
    CreatePostRequest request = new CreatePostRequest("Content", "https://example.com/image.jpg");

    when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(userRepository.findById(userId)).thenReturn(Optional.of(sampleAuthor(userId)));

    PostResponse response = postService.createPost(userId, request);

    assertEquals("https://example.com/image.jpg", response.mediaUrl());
  }

  @Test
  void createPost_Success_WithValidHttpUrl() {
    String userId = "usr_123";
    CreatePostRequest request = new CreatePostRequest("Content", "http://example.com/image.jpg");

    when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(userRepository.findById(userId)).thenReturn(Optional.of(sampleAuthor(userId)));

    PostResponse response = postService.createPost(userId, request);

    assertEquals("http://example.com/image.jpg", response.mediaUrl());
  }

  @Test
  void createPost_ThrowsPostValidationException_WhenAuthorUserNotFound() {
    String userId = "usr_nonexistent";
    CreatePostRequest request = new CreatePostRequest("Valid content", null);

    when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    PostValidationException exception =
        assertThrows(PostValidationException.class, () -> postService.createPost(userId, request));

    assertEquals("Author user not found", exception.getMessage());
  }

  @Test
  void createPost_GeneratesUniquePostIds() {
    String userId = "usr_123";
    CreatePostRequest request = new CreatePostRequest("Content", null);

    when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(userRepository.findById(userId)).thenReturn(Optional.of(sampleAuthor(userId)));

    PostResponse response1 = postService.createPost(userId, request);
    PostResponse response2 = postService.createPost(userId, request);

    assertNotNull(response1.id());
    assertNotNull(response2.id());
    assertNotEquals(response1.id(), response2.id());
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

    when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(userRepository.findById(userId)).thenReturn(Optional.of(author));

    PostResponse response = postService.createPost(userId, request);

    assertEquals("johndoe", response.author().username());
    assertEquals("John Doe", response.author().fullName());
    assertEquals("https://avatar.jpg", response.author().avatarUrl());
  }
}
