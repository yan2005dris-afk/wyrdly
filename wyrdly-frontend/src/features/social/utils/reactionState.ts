import type { Post, ReactionType } from "../../../types/feed";

/** Reaction-related slice of a Post. */
export type ReactionState = Pick<Post, "reactions" | "userReaction">;

/**
 * Moves the user's single reaction to `next`, mirroring the backend model:
 * one (:Usuario)-[:REACCIONA {tipo}]->(:Post) per (user, post) with a mutable
 * type. The previous type is decremented (never below 0) and the next one
 * incremented. Returns the same reference when nothing changes so callers can
 * skip re-renders.
 *
 * Single source of truth for optimistic updates, server reconciliation and
 * rollback — every transition (ADDED / REMOVED / UPDATED) is a move.
 */
export function withUserReaction<T extends ReactionState>(
  state: T,
  next: ReactionType | undefined,
): T {
  const prev = state.userReaction;
  if (prev === next) return state;

  const reactions: Record<ReactionType, number> = { ...state.reactions };
  if (prev) reactions[prev] = Math.max(0, (reactions[prev] ?? 0) - 1);
  if (next) reactions[next] = (reactions[next] ?? 0) + 1;

  return { ...state, reactions, userReaction: next };
}

/**
 * Client-side prediction of the backend toggle: clicking the active type
 * removes it, any other type sets (or replaces) it.
 */
export function predictToggle(
  current: ReactionType | undefined,
  clicked: ReactionType,
): ReactionType | undefined {
  return current === clicked ? undefined : clicked;
}
