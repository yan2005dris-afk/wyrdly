package com.wyrdly.post.domain.model;

import com.wyrdly.post.domain.exception.PostValidationException;
import java.time.Instant;
import java.util.Objects;

/**
 * Domain entity representing a user-authored comment attached to a {@link Post}.
 *
 * <p>Persisted in Neo4j as {@code (:Comentario)} linked by {@code (:Usuario)-[:ESCRIBE]->} and
 * {@code -[:EN_POST]->(:Post)}. Content rules mirror the HTTP {@code @NotBlank}/{@code @Size(1000)}
 * declarations on {@code CreateCommentRequest} but enforce invariants independently of JAX-RS, so
 * the model remains valid in non-REST contexts (events, background workers).
 */
public record Comment(String id, String postId, Author author, String content, Instant createdAt) {

  /** Maximum allowed comment length, enforced by the validator and the REST DTO. */
  public static final int MAX_CONTENT_LENGTH = 1000;

  public Comment {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(postId, "postId must not be null");
    Objects.requireNonNull(author, "author must not be null");
    if (content == null || content.trim().isEmpty()) {
      throw new PostValidationException("content must not be blank");
    }
    if (content.length() > MAX_CONTENT_LENGTH) {
      throw new PostValidationException("content exceeds " + MAX_CONTENT_LENGTH + " characters");
    }
    Objects.requireNonNull(createdAt, "createdAt must not be null");
  }
}
