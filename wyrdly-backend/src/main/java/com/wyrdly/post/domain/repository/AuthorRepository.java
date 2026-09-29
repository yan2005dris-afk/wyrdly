package com.wyrdly.post.domain.repository;

import com.wyrdly.post.domain.model.Author;
import java.util.Optional;

/**
 * Outbound port for resolving post authors.
 *
 * <p>Implemented in {@code post.infrastructure.persistence.Neo4jAuthorRepositoryAdapter} which
 * adapts from the auth bounded context. Returning {@link Optional#empty()} means the user does not
 * exist; infrastructure failures are propagated as runtime exceptions.
 */
public interface AuthorRepository {
  Optional<Author> findById(String userId);
}
