package com.wyrdly.post.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Value object describing why a post appears in someone else's timeline: it was shared (boosted) by
 * {@code reposter} at {@code repostedAt} through a {@code (:Usuario)-[:COMPARTE]->(:Post)}
 * relationship (HU #150).
 */
public record RepostContext(Author reposter, Instant repostedAt) {

  public RepostContext {
    Objects.requireNonNull(reposter, "reposter must not be null");
    Objects.requireNonNull(repostedAt, "repostedAt must not be null");
  }
}
