package com.wyrdly.post.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wyrdly.post.application.dto.CreatePostRequest;
import com.wyrdly.post.application.dto.PostResponse;
import com.wyrdly.post.domain.event.PostPublishedEvent;
import com.wyrdly.post.domain.exception.PostNotFoundException;
import com.wyrdly.post.domain.exception.PostPersistenceException;
import com.wyrdly.post.domain.exception.PostValidationException;
import com.wyrdly.post.domain.model.Author;
import com.wyrdly.post.domain.model.Post;
import com.wyrdly.post.domain.repository.AuthorRepository;
import com.wyrdly.post.domain.repository.PostRepository;
import jakarta.enterprise.event.Event;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link PostService}.
 *
 * <p>Input validation lives declaratively on {@link CreatePostRequest} and is exercised at the REST
 * boundary in {@code PostResourceTest}. This suite focuses on service-level business logic: author
 * resolution, ID generation, persistence orchestration, and exception mapping.
 */
class PostServiceTest {

  private PostRepository postRepository;
  private AuthorRepository authorRepository;
  private Event<PostPublishedEvent> postPublishedEvent;
  private PostService postService;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    postRepository = mock(PostRepository.class);
    authorRepository = mock(AuthorRepository.class);
    postPublishedEvent = mock(Event.class);
    doReturn(CompletableFuture.completedFuture(null))
        .when(postPublishedEvent)
        .fireAsync(any(PostPublishedEvent.class));
    postService = new PostService(postRepository, authorRepository, postPublishedEvent);
  }

  private Author sampleAuthor(String userId) {
    return new Author(userId, "testuser", "Test User", "https://avatar.jpg");
  }

  @Test
  void createPost_Success_WithValidContent() {
    String userId = "usr_123";
    CreatePostRequest request = new CreatePostRequest("This is a test post", null);

    when(authorRepository.findById(userId)).thenReturn(Optional.of(sampleAuthor(userId)));
    when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

    PostResponse response = postService.createPost(userId, request);

    assertNotNull(response);
    assertNotNull(response.id());
    assertEquals("This is a test post", response.content());
    assertEquals(userId, response.author().id());
    assertEquals("testuser", response.author().username());
    assertEquals("Test User", response.author().fullName());
    assertEquals("https://avatar.jpg", response.author().avatarUrl());
    assertEquals(0, response.reactionCounts().likeCount());
    assertEquals(0, response.reactionCounts().loveCount());
    assertEquals(0, response.reactionCounts().celebrateCount());
    assertEquals(null, response.userReaction());

    verify(authorRepository).findById(userId);
    verify(postRepository).save(any(Post.class));
  }

  @Test
  void createPost_Success_WithMediaUrl() {
    String userId = "usr_123";
    String mediaUrl = "https://example.com/image.jpg";
    CreatePostRequest request = new CreatePostRequest("Post with media", mediaUrl);

    when(authorRepository.findById(userId)).thenReturn(Optional.of(sampleAuthor(userId)));
    when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

    PostResponse response = postService.createPost(userId, request);

    assertEquals(mediaUrl, response.mediaUrl());
    verify(postRepository).save(any(Post.class));
  }

  @Test
  void createPost_EnrichesAuthorInformation() {
    String userId = "usr_123";
    CreatePostRequest request = new CreatePostRequest("Content", null);
    Author author = new Author(userId, "johndoe", "John Doe", "https://avatar.jpg");

    when(authorRepository.findById(userId)).thenReturn(Optional.of(author));
    when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

    PostResponse response = postService.createPost(userId, request);

    assertEquals("johndoe", response.author().username());
    assertEquals("John Doe", response.author().fullName());
    assertEquals("https://avatar.jpg", response.author().avatarUrl());
  }

  @Test
  void createPost_ThrowsPostValidationException_WhenAuthorNotFound() {
    String userId = "usr_nonexistent";
    CreatePostRequest request = new CreatePostRequest("Valid content", null);

    when(authorRepository.findById(userId)).thenReturn(Optional.empty());

    PostValidationException exception =
        assertThrows(PostValidationException.class, () -> postService.createPost(userId, request));

    assertEquals("Author user not found", exception.getMessage());
    verify(postRepository, never()).save(any(Post.class));
    verify(postPublishedEvent, never()).fireAsync(any(PostPublishedEvent.class));
  }

  @Test
  void createPost_PropagatesPostPersistenceException_FromSave() {
    String userId = "usr_123";
    CreatePostRequest request = new CreatePostRequest("Valid content", null);

    when(authorRepository.findById(userId)).thenReturn(Optional.of(sampleAuthor(userId)));
    when(postRepository.save(any(Post.class)))
        .thenThrow(new PostPersistenceException("Neo4j unavailable"));

    PostPersistenceException exception =
        assertThrows(PostPersistenceException.class, () -> postService.createPost(userId, request));

    assertEquals("Neo4j unavailable", exception.getMessage());
    verify(postPublishedEvent, never()).fireAsync(any(PostPublishedEvent.class));
  }

  @Test
  void createPost_FiresPostPublishedEventAsync_AfterSave() {
    String userId = "usr_123";
    CreatePostRequest request = new CreatePostRequest("Hello followers", null);

    when(authorRepository.findById(userId)).thenReturn(Optional.of(sampleAuthor(userId)));
    when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

    PostResponse response = postService.createPost(userId, request);

    ArgumentCaptor<PostPublishedEvent> captor = ArgumentCaptor.forClass(PostPublishedEvent.class);
    var inOrder = org.mockito.Mockito.inOrder(postRepository, postPublishedEvent);
    inOrder.verify(postRepository).save(any(Post.class));
    inOrder.verify(postPublishedEvent).fireAsync(captor.capture());
    verify(postPublishedEvent, never()).fire(any(PostPublishedEvent.class));

    PostPublishedEvent event = captor.getValue();
    assertEquals(response.id(), event.postId());
    assertEquals(userId, event.authorId());
    assertEquals("testuser", event.authorUsername());
    assertEquals("Hello followers", event.content());
    assertEquals(response.createdAt(), event.createdAt());
  }

  @Test
  void createPost_StillReturnsResponse_WhenAsyncObserverFails() {
    String userId = "usr_123";
    CreatePostRequest request = new CreatePostRequest("Content", null);

    when(authorRepository.findById(userId)).thenReturn(Optional.of(sampleAuthor(userId)));
    when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
    doReturn(CompletableFuture.failedFuture(new IllegalStateException("fan-out boom")))
        .when(postPublishedEvent)
        .fireAsync(any(PostPublishedEvent.class));

    PostResponse response = postService.createPost(userId, request);

    assertNotNull(response.id());
  }

  @Test
  void createPost_GeneratesUniquePostIds() {
    String userId = "usr_123";
    CreatePostRequest request = new CreatePostRequest("Content", null);

    when(authorRepository.findById(userId)).thenReturn(Optional.of(sampleAuthor(userId)));
    when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

    PostResponse response1 = postService.createPost(userId, request);
    PostResponse response2 = postService.createPost(userId, request);

    assertNotNull(response1.id());
    assertNotNull(response2.id());
    assertNotEquals(response1.id(), response2.id());
  }

  @Test
  void getPost_ReturnsPostResponse_WhenPostExists() {
    String userId = "usr_123";
    String postId = "pst_abc";
    com.wyrdly.post.domain.model.FeedPost feedPost =
        new com.wyrdly.post.domain.model.FeedPost(
            postId,
            "Sample content",
            null,
            java.time.Instant.now(),
            sampleAuthor("usr_author"),
            10L,
            5L,
            2L,
            4L,
            "LIKE");

    when(postRepository.findFeedPostById(postId, userId)).thenReturn(Optional.of(feedPost));

    PostResponse response = postService.getPost(userId, postId);

    assertNotNull(response);
    assertEquals(postId, response.id());
    assertEquals("Sample content", response.content());
    assertEquals("testuser", response.author().username());
    assertEquals(10L, response.reactionCounts().likeCount());
    assertEquals(4L, response.commentsCount());
    assertEquals("LIKE", response.userReaction());
  }

  @Test
  void getPost_ThrowsPostNotFoundException_WhenPostDoesNotExist() {
    String userId = "usr_123";
    String postId = "pst_missing";

    when(postRepository.findFeedPostById(postId, userId)).thenReturn(Optional.empty());

    assertThrows(PostNotFoundException.class, () -> postService.getPost(userId, postId));
  }
}
