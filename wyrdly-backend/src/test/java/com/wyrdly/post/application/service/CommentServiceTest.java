package com.wyrdly.post.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wyrdly.post.application.dto.CommentResponse;
import com.wyrdly.post.application.dto.CreateCommentRequest;
import com.wyrdly.post.domain.event.CommentCreatedEvent;
import com.wyrdly.post.domain.exception.CommentNotFoundException;
import com.wyrdly.post.domain.exception.PostNotFoundException;
import com.wyrdly.post.domain.exception.PostValidationException;
import com.wyrdly.post.domain.exception.UnauthorizedCommentActionException;
import com.wyrdly.post.domain.model.Author;
import com.wyrdly.post.domain.model.Comment;
import com.wyrdly.post.domain.model.Post;
import com.wyrdly.post.domain.repository.AuthorRepository;
import com.wyrdly.post.domain.repository.CommentRepository;
import com.wyrdly.post.domain.repository.PostRepository;
import jakarta.enterprise.event.Event;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link CommentService}.
 *
 * <p>Service-level coverage for the three use cases: happy paths, missing post/author, missing
 * comment, the post-id-mismatch security invariant (S1), and authorization rules (comment author OR
 * post owner can delete).
 */
class CommentServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

  private CommentRepository commentRepository;
  private PostRepository postRepository;
  private AuthorRepository authorRepository;
  private Event<CommentCreatedEvent> commentCreatedEvent;
  private CommentService commentService;

  private static Author sampleAuthor(String userId) {
    return new Author(userId, "testuser", "Test User", "https://avatar.jpg");
  }

  private static Post samplePost(String postId, String userId) {
    return new Post(postId, userId, "Body", null, NOW);
  }

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    commentRepository = mock(CommentRepository.class);
    postRepository = mock(PostRepository.class);
    authorRepository = mock(AuthorRepository.class);
    commentCreatedEvent = mock(Event.class);
    commentService =
        new CommentService(
            commentRepository, postRepository, authorRepository, commentCreatedEvent);
  }

  // ---------------------------------------------------------------------------
  // createComment
  // ---------------------------------------------------------------------------

  @Test
  void createCommentSuccess() {
    String postId = "pst_abc";
    String userId = "usr_alice";
    CreateCommentRequest request = new CreateCommentRequest("Great post!");
    Author author = sampleAuthor(userId);

    when(postRepository.findById(postId)).thenReturn(Optional.of(samplePost(postId, "usr_bob")));
    when(authorRepository.findById(userId)).thenReturn(Optional.of(author));
    when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

    CommentResponse response = commentService.createComment(postId, userId, request);

    assertNotNull(response);
    assertEquals(postId, response.postId());
    assertEquals(userId, response.authorId());
    assertEquals("Great post!", response.content());
    assertEquals(userId, response.author().id());
    verify(commentRepository).save(any(Comment.class));
    verify(commentCreatedEvent).fire(any(CommentCreatedEvent.class));
  }

  @Test
  void createCommentFailsWhenPostNotFound() {
    String postId = "pst_missing";
    when(postRepository.findById(postId)).thenReturn(Optional.empty());

    assertThrows(
        PostNotFoundException.class,
        () -> commentService.createComment(postId, "usr_alice", new CreateCommentRequest("Hi")));

    verify(commentRepository, never()).save(any(Comment.class));
    verify(commentCreatedEvent, never()).fire(any());
  }

  @Test
  void createCommentPersistsAndFiresEvent() {
    String postId = "pst_abc";
    String userId = "usr_alice";
    String postAuthorId = "usr_bob";
    CreateCommentRequest request = new CreateCommentRequest("  Hello world  ");

    when(postRepository.findById(postId)).thenReturn(Optional.of(samplePost(postId, postAuthorId)));
    when(authorRepository.findById(userId)).thenReturn(Optional.of(sampleAuthor(userId)));
    when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

    commentService.createComment(postId, userId, request);

    ArgumentCaptor<Comment> commentCaptor = ArgumentCaptor.forClass(Comment.class);
    verify(commentRepository).save(commentCaptor.capture());
    Comment saved = commentCaptor.getValue();
    assertEquals("Hello world", saved.content(), "Content must be trimmed");

    ArgumentCaptor<CommentCreatedEvent> eventCaptor =
        ArgumentCaptor.forClass(CommentCreatedEvent.class);
    verify(commentCreatedEvent).fire(eventCaptor.capture());
    CommentCreatedEvent fired = eventCaptor.getValue();
    assertEquals(postId, fired.postId());
    assertEquals(postAuthorId, fired.postAuthorId(), "postAuthorId must be the post owner");
    assertEquals(userId, fired.commentAuthorId());
    assertEquals("Hello world", fired.content());
  }

  @Test
  void createCommentFailsWhenUserNotFound() {
    String postId = "pst_abc";
    String userId = "usr_ghost";
    when(postRepository.findById(postId)).thenReturn(Optional.of(samplePost(postId, "usr_bob")));
    when(authorRepository.findById(userId)).thenReturn(Optional.empty());

    assertThrows(
        PostValidationException.class,
        () -> commentService.createComment(postId, userId, new CreateCommentRequest("Hi")));

    verify(commentRepository, never()).save(any(Comment.class));
  }

  // ---------------------------------------------------------------------------
  // getComments
  // ---------------------------------------------------------------------------

  @Test
  void getCommentsReturnsEmptyWhenNoComments() {
    String postId = "pst_abc";
    when(postRepository.findById(postId)).thenReturn(Optional.of(samplePost(postId, "usr_bob")));
    when(commentRepository.findByPostId(postId, 1, 20)).thenReturn(List.of());
    when(commentRepository.countByPostId(postId)).thenReturn(0L);

    var response = commentService.getComments(postId, 1, 20);

    assertEquals(0, response.data().size());
    assertEquals(0L, response.totalCount());
    assertEquals(1, response.page());
    assertEquals(20, response.pageSize());
  }

  @Test
  void getCommentsReturnsPaginatedList() {
    String postId = "pst_abc";
    Author author = sampleAuthor("usr_alice");
    Comment c1 = new Comment("cmt_1", postId, author, "First", NOW);
    Comment c2 = new Comment("cmt_2", postId, author, "Second", NOW.plusSeconds(1));

    when(postRepository.findById(postId)).thenReturn(Optional.of(samplePost(postId, "usr_bob")));
    when(commentRepository.findByPostId(postId, 1, 2)).thenReturn(List.of(c1, c2));
    when(commentRepository.countByPostId(postId)).thenReturn(2L);

    var response = commentService.getComments(postId, 1, 2);

    assertEquals(2, response.data().size());
    assertEquals("cmt_1", response.data().get(0).id());
    assertEquals("usr_alice", response.data().get(0).authorId());
    assertEquals("First", response.data().get(0).content());
    assertEquals(2L, response.totalCount());
    assertEquals(2, response.pageSize());
  }

  @Test
  void getCommentsFailsWhenPostNotFound() {
    String postId = "pst_missing";
    when(postRepository.findById(postId)).thenReturn(Optional.empty());

    assertThrows(PostNotFoundException.class, () -> commentService.getComments(postId, 1, 20));
    verify(commentRepository, never()).findByPostId(eq(postId), anyInt(), anyInt());
  }

  // ---------------------------------------------------------------------------
  // deleteComment
  // ---------------------------------------------------------------------------

  @Test
  void deleteCommentSucceedsForCommentAuthor() {
    String postId = "pst_abc";
    String commentId = "cmt_1";
    String userId = "usr_alice";
    String postOwner = "usr_bob";
    Author author = sampleAuthor(userId);

    when(postRepository.findById(postId)).thenReturn(Optional.of(samplePost(postId, postOwner)));
    when(commentRepository.findById(commentId))
        .thenReturn(Optional.of(new Comment(commentId, postId, author, "Hi", NOW)));

    commentService.deleteComment(postId, commentId, userId);

    verify(commentRepository).deleteById(commentId);
  }

  @Test
  void deleteCommentSucceedsForPostOwner() {
    String postId = "pst_abc";
    String commentId = "cmt_1";
    String userId = "usr_bob"; // post owner, not comment author
    String postOwner = "usr_bob";
    Author author = sampleAuthor("usr_alice");

    when(postRepository.findById(postId)).thenReturn(Optional.of(samplePost(postId, postOwner)));
    when(commentRepository.findById(commentId))
        .thenReturn(Optional.of(new Comment(commentId, postId, author, "Hi", NOW)));

    commentService.deleteComment(postId, commentId, userId);

    verify(commentRepository).deleteById(commentId);
  }

  @Test
  void deleteCommentThrowsUnauthorizedForOtherUser() {
    String postId = "pst_abc";
    String commentId = "cmt_1";
    String userId = "usr_charlie";
    String postOwner = "usr_bob";
    Author author = sampleAuthor("usr_alice");

    when(postRepository.findById(postId)).thenReturn(Optional.of(samplePost(postId, postOwner)));
    when(commentRepository.findById(commentId))
        .thenReturn(Optional.of(new Comment(commentId, postId, author, "Hi", NOW)));

    assertThrows(
        UnauthorizedCommentActionException.class,
        () -> commentService.deleteComment(postId, commentId, userId));
    verify(commentRepository, never()).deleteById(any());
  }

  @Test
  void deleteCommentThrowsNotFoundWhenCommentMissing() {
    String postId = "pst_abc";
    String commentId = "cmt_missing";

    when(postRepository.findById(postId)).thenReturn(Optional.of(samplePost(postId, "usr_bob")));
    when(commentRepository.findById(commentId)).thenReturn(Optional.empty());

    assertThrows(
        CommentNotFoundException.class,
        () -> commentService.deleteComment(postId, commentId, "usr_alice"));
    verify(commentRepository, never()).deleteById(any());
  }

  /**
   * S1 regression test: when the comment exists but belongs to a different post than the one in the
   * path, the service must report {@link CommentNotFoundException} (404), not {@link
   * UnauthorizedCommentActionException} (403). Returning 403 here would leak existence.
   */
  @Test
  void deleteCommentThrowsNotFoundWhenCommentBelongsToDifferentPost() {
    String pathPostId = "pst_path";
    String commentId = "cmt_1";
    String actualPostId = "pst_other";
    String userId = "usr_alice";
    String pathPostOwner = "usr_bob";
    Author author = sampleAuthor("usr_alice");

    when(postRepository.findById(pathPostId))
        .thenReturn(Optional.of(samplePost(pathPostId, pathPostOwner)));
    when(commentRepository.findById(commentId))
        .thenReturn(Optional.of(new Comment(commentId, actualPostId, author, "Hi", NOW)));

    assertThrows(
        CommentNotFoundException.class,
        () -> commentService.deleteComment(pathPostId, commentId, userId));
    verify(commentRepository, never()).deleteById(any());
  }
}
