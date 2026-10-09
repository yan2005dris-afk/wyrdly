import { useCallback, useState } from "react";
import { isCancel } from "axios";
import { postsApi, type RepostResponse } from "../../../api/posts";

interface RepostState {
  isPending: boolean;
  error: string | null;
}

export interface UseRepostCallbacks {
  onOptimistic?: (reposted: boolean) => void;
  onServerResult?: (response: RepostResponse) => void;
  onRollback?: () => void;
}

export interface UseRepostReturn {
  repost: (
    postId: string,
    reposted: boolean,
    callbacks?: UseRepostCallbacks,
  ) => Promise<void>;
  isPending: (postId: string) => boolean;
  getError: (postId: string) => string | null;
}

/**
 * Module-level registry of in-flight AbortControllers, keyed by postId.
 * Independent from reaction requests so reactions and reposts do not cancel each other.
 */
const inFlight = new Map<string, AbortController>();

/**
 * Hook wrapping PUT/DELETE /api/posts/{postId}/repost (HU #144).
 */
export function useRepost(): UseRepostReturn {
  const [state, setState] = useState<Record<string, RepostState>>({});

  const repost = useCallback(
    async (
      postId: string,
      reposted: boolean,
      callbacks: UseRepostCallbacks = {},
    ): Promise<void> => {
      // Cancel previous in-flight repost request for this post
      const prev = inFlight.get(postId);
      if (prev) prev.abort();

      const controller = new AbortController();
      inFlight.set(postId, controller);

      setState((s) => ({ ...s, [postId]: { isPending: true, error: null } }));
      callbacks.onOptimistic?.(reposted);

      try {
        const response = await postsApi.setRepost(
          postId,
          reposted,
          controller.signal,
        );
        if (inFlight.get(postId) === controller) {
          callbacks.onServerResult?.(response);
          setState((s) => ({
            ...s,
            [postId]: { isPending: false, error: null },
          }));
        }
      } catch (err) {
        const stillActive = inFlight.get(postId) === controller;

        if (isCancel(err)) {
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

  return { repost, isPending, getError };
}
