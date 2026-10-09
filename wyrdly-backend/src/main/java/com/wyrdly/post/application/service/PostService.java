package com.wyrdly.post.application.service;

import com.wyrdly.post.application.dto.CreatePostRequest;
import com.wyrdly.post.application.dto.PostResponse;
import com.wyrdly.post.application.dto.PostResponse.AuthorDto;
import com.wyrdly.post.application.mapper.PostResponseMapper;
import com.wyrdly.post.application.usecase.CreatePostUseCase;
import com.wyrdly.post.application.usecase.GetPostUseCase;
import com.wyrdly.post.domain.event.PostPublishedEvent;
import com.wyrdly.post.domain.exception.PostNotFoundException;
import com.wyrdly.post.domain.exception.PostValidationException;
import com.wyrdly.post.domain.model.Author;
import com.wyrdly.post.domain.model.FeedPost;
import com.wyrdly.post.domain.model.Post;
import com.wyrdly.post.domain.repository.AuthorRepository;
import com.wyrdly.post.domain.repository.PostRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@ApplicationScoped
public class PostService implements CreatePostUseCase, GetPostUseCase {

  private final PostRepository postRepository;
  private final AuthorRepository authorRepository;
  private final Event<PostPublishedEvent> postPublishedEvent;

  @Inject
  public PostService(
      PostRepository postRepository,
      AuthorRepository authorRepository,
      Event<PostPublishedEvent> postPublishedEvent) {
    this.postRepository = Objects.requireNonNull(postRepository, "postRepository must not be null");
    this.authorRepository =
        Objects.requireNonNull(authorRepository, "authorRepository must not be null");
    this.postPublishedEvent =
        Objects.requireNonNull(postPublishedEvent, "postPublishedEvent must not be null");
  }

  @Override
  public PostResponse createPost(String userId, CreatePostRequest request) {
    Author author =
        authorRepository
            .findById(userId)
            .orElseThrow(() -> new PostValidationException("Author user not found"));

    String postId = "pst_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    Instant createdAt = Instant.now();
    Post post = new Post(postId, userId, request.content(), request.mediaUrl(), createdAt);

    Post saved = postRepository.save(post);

    Log.infof("Post created successfully: id=%s, authorId=%s", saved.id(), userId);

    // Fired after the write transaction committed, asynchronously: the follower fan-out (HU11
    // NEW_POST_FROM_FOLLOWED) must never delay the 201 nor fail the request.
    postPublishedEvent
        .fireAsync(
            new PostPublishedEvent(
                saved.id(), userId, author.username(), saved.content(), saved.createdAt()))
        .exceptionally(
            ex -> {
              Log.warnf(ex, "PostPublishedEvent observer failed: postId=%s", saved.id());
              return null;
            });

    return new PostResponse(
        saved.id(),
        saved.content(),
        saved.mediaUrl(),
        saved.createdAt(),
        new AuthorDto(author.id(), author.username(), author.fullName(), author.avatarUrl()),
        new PostResponse.ReactionCounts(0, 0, 0),
        0L,
        null);
  }

  @Override
  public PostResponse getPost(String userId, String postId) {
    FeedPost feedPost =
        postRepository
            .findFeedPostById(postId, userId)
            .orElseThrow(() -> new PostNotFoundException("Post not found: " + postId));
    return PostResponseMapper.toResponse(feedPost);
  }
}
