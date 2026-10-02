package com.wyrdly.post.domain.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Allowed reaction types a user can apply to a post. Mapped to the {@code tipo} property on the
 * {@code (:Usuario)-[:REACCIONA {tipo}]->(:Post)} relationship.
 */
@JsonDeserialize(using = ReactionTypeDeserializer.class)
public enum ReactionType {
  LIKE,
  LOVE,
  CELEBRATE
}
