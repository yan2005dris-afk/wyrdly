package com.wyrdly.post.application.service;

import com.wyrdly.auth.domain.model.User;
import com.wyrdly.auth.domain.repository.UserRepository;
import com.wyrdly.post.application.dto.CreatePostRequest;
import com.wyrdly.post.application.dto.PostResponse;
import com.wyrdly.post.application.dto.PostResponse.AuthorDto;
import com.wyrdly.post.application.usecase.CreatePostUseCase;
import com.wyrdly.post.domain.exception.PostValidationException;
import com.wyrdly.post.domain.model.Post;
import com.wyrdly.post.domain.repository.PostRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class PostService implements CreatePostUseCase {

  private final PostRepository postRepository;
  private final UserRepository userRepository;

  @Inject
  public PostService(PostRepository postRepository, UserRepository userRepository) {
    this.postRepository = postRepository;
    this.userRepository = userRepository;
  }

  @Override
  public PostResponse createPost(String userId, CreatePostRequest request) {
    validateRequest(request);

    String postId = "pst_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    Instant createdAt = Instant.now();

    Post post = new Post(postId, userId, request.content(), request.mediaUrl(), createdAt);

    Post saved = postRepository.save(post);

    User author =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new PostValidationException("Author user not found"));

    Log.infof("Post created successfully: id=%s, authorId=%s", saved.id(), userId);

    return new PostResponse(
        saved.id(),
        saved.content(),
        saved.mediaUrl(),
        saved.createdAt(),
        new AuthorDto(author.id(), author.username(), author.fullName(), author.avatarUrl()),
        Map.of("LIKE", 0),
        null);
  }

  private void validateRequest(CreatePostRequest request) {
    if (request.content() == null || request.content().isBlank()) {
      throw new PostValidationException("Content is required");
    }
    if (request.content().length() > 1000) {
      throw new PostValidationException("Content must not exceed 1000 characters");
    }
    if (request.mediaUrl() != null && !request.mediaUrl().isBlank()) {
      if (!isValidUrl(request.mediaUrl())) {
        throw new PostValidationException("Invalid media URL format");
      }
    }
  }

  private boolean isValidUrl(String url) {
    try {
      new java.net.URL(url);
      return true;
    } catch (java.net.MalformedURLException e) {
      return false;
    }
  }
}
