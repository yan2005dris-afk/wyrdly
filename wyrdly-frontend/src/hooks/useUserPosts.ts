import { useCallback, useEffect, useState } from "react";
import { usersApi } from "../api/users";
import type { PostApiResponse } from "../types/feed";

interface UseUserPostsReturn {
  readonly posts: readonly PostApiResponse[];
  readonly isLoading: boolean;
  readonly error: string | null;
  readonly refetch: () => void;
}

interface UseUserPostsParams {
  readonly page?: number;
  readonly pageSize?: number;
}

/**
 * Load the paginated list of posts a given user has published.
 *
 * The result is intentionally a list of `PostApiResponse` (the
 * backend's exact envelope) rather than the UI-shaped `Post`. The
 * caller can hydrate into `Post` via `mapPostApiResponseToPost` if
 * they need to render with the existing PostCard. Keeping the raw
 * shape here means the profile gallery does not have to invent
 * stats, reactions or comments that the backend does not return
 * for that endpoint yet.
 */
export function useUserPosts(
  username: string | undefined,
  params: UseUserPostsParams = {},
): UseUserPostsReturn {
  const [posts, setPosts] = useState<readonly PostApiResponse[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(Boolean(username));
  const [error, setError] = useState<string | null>(null);

  const fetchPosts = useCallback(async () => {
    if (!username) {
      setPosts([]);
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    setError(null);

    try {
      const data = await usersApi.getUserPosts(username, params);
      setPosts(data);
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "Failed to load posts";
      setError(message);
      setPosts([]);
    } finally {
      setIsLoading(false);
    }
  }, [username, params.page, params.pageSize]);

  useEffect(() => {
    let isCancelled = false;

    if (!username) {
      return;
    }

    const load = async () => {
      try {
        const data = await usersApi.getUserPosts(username, params);
        if (!isCancelled) {
          setPosts(data);
          setError(null);
        }
      } catch (err) {
        if (!isCancelled) {
          const message =
            err instanceof Error ? err.message : "Failed to load posts";
          setError(message);
          setPosts([]);
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
  }, [username, params.page, params.pageSize]);

  return { posts, isLoading, error, refetch: fetchPosts };
}