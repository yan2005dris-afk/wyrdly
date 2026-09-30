import { useState, useCallback } from "react";
import { usersApi } from "../../../api/users";

interface UseFollowReturn {
  readonly follow: (targetUserId: string) => Promise<void>;
  readonly unfollow: (targetUserId: string) => Promise<void>;
  readonly isMutating: boolean;
  readonly error: string | null;
}

export function useFollow(): UseFollowReturn {
  const [isMutating, setIsMutating] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const follow = useCallback(async (targetUserId: string): Promise<void> => {
    setIsMutating(true);
    setError(null);

    try {
      await usersApi.follow(targetUserId);
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "Failed to follow user";
      setError(message);
      throw err;
    } finally {
      setIsMutating(false);
    }
  }, []);

  const unfollow = useCallback(async (targetUserId: string): Promise<void> => {
    setIsMutating(true);
    setError(null);

    try {
      await usersApi.unfollow(targetUserId);
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "Failed to unfollow user";
      setError(message);
      throw err;
    } finally {
      setIsMutating(false);
    }
  }, []);

  return {
    follow,
    unfollow,
    isMutating,
    error,
  };
}
