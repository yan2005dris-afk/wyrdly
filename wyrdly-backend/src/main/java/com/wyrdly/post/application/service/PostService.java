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
}
