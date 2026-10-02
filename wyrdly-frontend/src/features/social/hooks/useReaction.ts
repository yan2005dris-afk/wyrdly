import { useCallback, useState } from "react";
import { isCancel } from "axios";
import {
  postsApi,
  type ReactionType,
  type ReactPostResponse,
} from "../../../api/posts";

/**
 * Per-postId status tracked by useReaction. The hook deliberately does NOT
 * own feed state (that's useFeed's job) — only the transient request state
 * (in-flight + last error).
 */
interface ReactionState {
  isPending: boolean;
  error: string | null;
}

/**
 * Lifecycle callbacks invoked by `react()`. The hook is intentionally
 * state-agnostic: callers wire these to their own store (useFeed's
 * replacePost / refetch).
 *
 * - onOptimistic  → fired synchronously after the request starts.
 *                  Use it to apply an optimistic update.
 * - onServerResult → fired when the backend confirms the mutation.
 *                    Use it to reconcile with authoritative state.
 * - onRollback    → fired when the request failed (non-cancel).
 *                  Use it to restore the pre-optimistic snapshot.
 */
export interface UseReactionCallbacks {
  onOptimistic?: (type: ReactionType) => void;
  onServerResult?: (response: ReactPostResponse) => void;
  onRollback?: () => void;
}

export interface UseReactionReturn {
  react: (
    postId: string,
    type: ReactionType,
    callbacks?: UseReactionCallbacks,
  ) => Promise<void>;
  isPending: (postId: string) => boolean;
  getError: (postId: string) => string | null;
}

/**
 * Module-level registry of in-flight AbortControllers, keyed by postId.
 * Lives outside the hook so cancellations are observed across remounts
 * of consumers (e.g. feed list virtualization).
 */
const inFlight = new Map<string, AbortController>();

/**
 * Hook that wraps POST /api/posts/{postId}/react (HU09).
 *
 * Behavior:
 * - Cancels any in-flight request for the same postId before starting a new one.
 * - Tracks per-postId isPending / error state.
 * - Cancelled requests are silent (no rollback, no error).
 * - Non-cancel errors trigger `onRollback` and store the message.
 *
 * Note: feed state mutation is delegated to the caller's callbacks. This
 * keeps the hook decoupled from useFeed and reusable outside the feed.
 */
export function useReaction(): UseReactionReturn {
  const [state, setState] = useState<Record<string, ReactionState>>({});

  const react = useCallback(
    async (
      postId: string,
      type: ReactionType,
      callbacks: UseReactionCallbacks = {},
    ): Promise<void> => {
      // Cancel any previous in-flight request for this post.
      const prev = inFlight.get(postId);
      if (prev) prev.abort();

      const controller = new AbortController();
      inFlight.set(postId, controller);

      setState((s) => ({ ...s, [postId]: { isPending: true, error: null } }));
      callbacks.onOptimistic?.(type);

      try {
        const response = await postsApi.react(postId, type, controller.signal);
        callbacks.onServerResult?.(response);
        // Only mutate state if we are still the active controller for this
        // postId. A newer react() call will manage its own lifecycle.
        if (inFlight.get(postId) === controller) {
          setState((s) => ({
            ...s,
            [postId]: { isPending: false, error: null },
          }));
        }
      } catch (err) {
        // Suppress state writes if a newer request took over for this postId.
        const stillActive = inFlight.get(postId) === controller;

        if (isCancel(err)) {
          // Silent: cancellation is expected when a newer click arrives.
          // Reset pending state only when no successor took over.
          if (stillActive) {
            setState((s) => ({
              ...s,
              [postId]: { isPending: false, error: null },
            }));
          }
          return;
        }
        if (stillActive) {
          callbacks.onRollback?.();
          setState((s) => ({
            ...s,
            [postId]: {
              isPending: false,
              error: err instanceof Error ? err.message : String(err),
            },
          }));
        }
      } finally {
        // Only clear the registry entry if it's still ours — a newer request
        // for the same postId may have replaced it.
        if (inFlight.get(postId) === controller) {
          inFlight.delete(postId);
        }
      }
    },
    [],
  );

  const isPending = useCallback(
    (postId: string): boolean => state[postId]?.isPending ?? false,
    [state],
  );

  const getError = useCallback(
    (postId: string): string | null => state[postId]?.error ?? null,
    [state],
  );

  return { react, isPending, getError };
}
