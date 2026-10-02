package com.wyrdly.post.domain.model;

/**
 * Allowed reaction types a user can apply to a post. Mapped to the {@code tipo} property on the
 * {@code (:Usuario)-[:REACCIONA {tipo}]->(:Post)} relationship.
 */
public enum ReactionType {
  LIKE,
  LOVE,
  CELEBRATE
}
