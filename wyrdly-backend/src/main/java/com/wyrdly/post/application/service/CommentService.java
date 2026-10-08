package com.wyrdly.post.application.service;

import com.wyrdly.post.application.dto.CommentListResponseDto;
import com.wyrdly.post.application.dto.CommentResponse;
import com.wyrdly.post.application.dto.CreateCommentRequest;
import com.wyrdly.post.application.dto.PostResponse.AuthorDto;
import com.wyrdly.post.application.usecase.CreateCommentUseCase;
import com.wyrdly.post.application.usecase.DeleteCommentUseCase;
import com.wyrdly.post.application.usecase.ListCommentsByPostUseCase;
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
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Orchestrator for the three comment use cases. Implements the segregation principle by exposing
 * one interface per verb (Create / List / Delete) so REST resources depend only on the verbs they
 * actually use.
 *
 * <p>Authorization for delete is intentionally checked AFTER the comment-belongs-to-post invariant
 * so that we never leak "this comment exists but you cannot see it" information: a mismatched
 * {@code postId} is reported as {@link CommentNotFoundException} (404) rather than {@link
 * UnauthorizedCommentActionException} (403).
 */
@ApplicationScoped
public class CommentService
    implements CreateCommentUseCase, ListCommentsByPostUseCase, DeleteCommentUseCase {

  private final CommentRepository commentRepository;
  private final PostRepository postRepository;
  private final AuthorRepository authorRepository;
  private final Event<CommentCreatedEvent> commentCreatedEvent;

  @Inject
  public CommentService(
      CommentRepository commentRepository,
      PostRepository postRepository,
      AuthorRepository authorRepository,
      Event<CommentCreatedEvent> commentCreatedEvent) {
    this.commentRepository = Objects.requireNonNull(commentRepository, "commentRepository");
    this.postRepository = Objects.requireNonNull(postRepository, "postRepository");
    this.authorRepository = Objects.requireNonNull(authorRepository, "authorRepository");
    this.commentCreatedEvent = Objects.requireNonNull(commentCreatedEvent, "commentCreatedEvent");
  }

  /**
   * Creates a new comment after verifying that both the post and the author exist. Persists the
   * comment, fires a {@link CommentCreatedEvent} for the notifications bounded context, and returns
   * the enriched response DTO.
   *
   * @throws PostNotFoundException if the post does not exist
   * @throws PostValidationException if the author does not exist (kept as 400 for caller-friendly
   *     handling — typically this is a JWT-user namespace mismatch)
   */
  @Override
  @Transactional
  public CommentResponse createComment(String postId, String userId, CreateCommentRequest request) {
    Post post =
        postRepository.findById(postId).orElseThrow(() -> new PostNotFoundException(postId));

    Author author =
        authorRepository
            .findById(userId)
            .orElseThrow(() -> new PostValidationException("User not found: " + userId));

    String commentId = "cmt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    Instant now = Instant.now();

    Comment comment = new Comment(commentId, postId, author, request.content().trim(), now);
    Comment saved = commentRepository.save(comment);

    Log.infof("Comment created: id=%s, postId=%s, authorId=%s", saved.id(), postId, userId);

    // Fire domain event for notifications / push dispatch
    commentCreatedEvent.fire(
        new CommentCreatedEvent(saved.id(), postId, post.userId(), userId, saved.content(), now));

    AuthorDto authorDto =
        new AuthorDto(author.id(), author.username(), author.fullName(), author.avatarUrl());
    return new CommentResponse(
        saved.id(), saved.postId(), author.id(), saved.content(), saved.createdAt(), authorDto);
  }

  /**
   * Returns the paginated comments of a post. Validates that the post exists first so missing posts
   * do not silently return empty lists.
   *
   * @throws PostNotFoundException if the post does not exist
   */
  @Override
  @Transactional
  public CommentListResponseDto getComments(String postId, int page, int pageSize) {
    if (postRepository.findById(postId).isEmpty()) {
      throw new PostNotFoundException(postId);
    }
    List<Comment> comments = commentRepository.findByPostId(postId, page, pageSize);
    long total = commentRepository.countByPostId(postId);

    List<CommentResponse> responses =
        comments.stream()
            .map(
                c -> {
                  Author a = c.author();
                  AuthorDto dto = new AuthorDto(a.id(), a.username(), a.fullName(), a.avatarUrl());
                  return new CommentResponse(
                      c.id(), c.postId(), a.id(), c.content(), c.createdAt(), dto);
                })
            .toList();

    return new CommentListResponseDto(responses, total, page, pageSize);
  }

  /**
   * Deletes a comment. Authorization: comment author OR post owner.
   *
   * <p><b>Order of checks (security):</b> {@code comment.postId() == pathPostId} is verified BEFORE
   * the authorization check so that a request with a wrong {@code postId} yields 404, not 403. This
   * avoids an enumeration side-channel — an attacker probing {@code DELETE
   * /api/posts/{A}/comments/{X}} for unrelated {@code X} cannot distinguish "comment exists but you
   * are not authorized" from "comment does not exist".
   *
   * @throws PostNotFoundException if the post does not exist
   * @throws CommentNotFoundException if the comment does not exist or does not belong to the path
   *     post
   * @throws UnauthorizedCommentActionException if the requesting user is neither the comment author
   *     nor the post owner
   */
  @Override
  @Transactional
  public void deleteComment(String postId, String commentId, String userId) {
    Post post =
        postRepository.findById(postId).orElseThrow(() -> new PostNotFoundException(postId));

    Comment comment =
        commentRepository
            .findById(commentId)
            .orElseThrow(() -> new CommentNotFoundException(commentId));

    // S1 FIX: verify the comment belongs to the path post BEFORE auth, to prevent enumeration
    if (!comment.postId().equals(postId)) {
      throw new CommentNotFoundException(commentId, postId);
    }

    boolean isCommentAuthor = comment.author().id().equals(userId);
    boolean isPostOwner = post.userId().equals(userId);

    if (!isCommentAuthor && !isPostOwner) {
      throw new UnauthorizedCommentActionException(
          "User " + userId + " is not authorized to delete comment " + commentId);
    }

    commentRepository.deleteById(commentId);
    Log.infof("Comment deleted: id=%s, by userId=%s", commentId, userId);
  }
}
