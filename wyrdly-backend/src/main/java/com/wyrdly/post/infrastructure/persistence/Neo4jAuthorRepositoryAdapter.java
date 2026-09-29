package com.wyrdly.post.infrastructure.persistence;

import com.wyrdly.auth.domain.repository.UserRepository;
import com.wyrdly.post.domain.model.Author;
import com.wyrdly.post.domain.repository.AuthorRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;

/**
 * Adapter that resolves a post author by delegating to the auth bounded context's user store and
 * projecting its {@code User} aggregate into the post {@link Author} model.
 *
 * <p>This is the only place in the post module that knows about auth's domain types — the
 * application/domain layers consume {@link Author} and {@link AuthorRepository} exclusively.
 */
@ApplicationScoped
public class Neo4jAuthorRepositoryAdapter implements AuthorRepository {

  private final UserRepository userRepository;

  @Inject
  public Neo4jAuthorRepositoryAdapter(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public Optional<Author> findById(String userId) {
    return userRepository
        .findById(userId)
        .map(user -> new Author(user.id(), user.username(), user.fullName(), user.avatarUrl()));
  }
}
