import { useCallback, useEffect, useState } from "react";
import { usersApi } from "../api/users";
import type { ProfileUserSummary } from "../types/suggestions";

interface UseProfileUsersReturn {
  readonly users: readonly ProfileUserSummary[];
  readonly isLoading: boolean;
  readonly error: string | null;
  readonly refetch: () => void;
}

interface UseProfileUsersParams {
  readonly page?: number;
  readonly pageSize?: number;
}

/**
 * Load the paginated list of users that either follow {@code username}
 * (followers) or are followed by it (following). Both endpoints share
 * the same response shape, so a single hook covers both directions.
 *
 * `direction` selects the endpoint: "followers" or "following".
 */
export function useProfileUsers(
  username: string | undefined,
  direction: "followers" | "following",
  params: UseProfileUsersParams = {},
): UseProfileUsersReturn {
  const [users, setUsers] = useState<readonly ProfileUserSummary[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(Boolean(username));
  const [error, setError] = useState<string | null>(null);

  const fetcher =
    direction === "followers"
      ? usersApi.getUserFollowers
      : usersApi.getUserFollowing;

  const fetchUsers = useCallback(async () => {
    if (!username) {
      setUsers([]);
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    setError(null);

    try {
      const data = await fetcher(username, params);
      setUsers(data);
    } catch (err) {
      const message =
        err instanceof Error ? err.message : `Failed to load ${direction}`;
      setError(message);
      setUsers([]);
    } finally {
      setIsLoading(false);
    }
  }, [username, direction, params.page, params.pageSize]);

  useEffect(() => {
    let isCancelled = false;

    if (!username) {
      return;
    }

    const load = async () => {
      try {
        const data = await fetcher(username, params);
        if (!isCancelled) {
          setUsers(data);
          setError(null);
        }
      } catch (err) {
        if (!isCancelled) {
          const message =
            err instanceof Error ? err.message : `Failed to load ${direction}`;
          setError(message);
          setUsers([]);
        }
      } finally {
        if (!isCancelled) {
          setIsLoading(false);
        }
      }
    };

    void load();

    return () => {
      isCancelled = true;
    };
  }, [username, direction, params.page, params.pageSize]);

  return { users, isLoading, error, refetch: fetchUsers };
}
