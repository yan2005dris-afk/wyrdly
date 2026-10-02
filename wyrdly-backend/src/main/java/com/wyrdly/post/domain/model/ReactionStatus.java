package com.wyrdly.post.domain.model;

/**
 * Outcome of a reaction toggle request. Communicated to the client so the UI can reconcile its
 * optimistic state with the server result.
 */
public enum ReactionStatus {
  /** A new relationship was created. */
  ADDED,
  /** An existing relationship (same type) was removed. */
  REMOVED,
  /** An existing relationship was switched to a different reaction type. */
  UPDATED
}
