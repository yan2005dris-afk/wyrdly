import { useCallback, useEffect, useState } from "react";
import { postsApi } from "../../../api/posts";
import type { Post } from "../../../types/feed";
import { mapPostApiResponseToPost } from "../../../types/feed";

export interface UseFeedReturn {
  readonly posts: readonly Post[];
  readonly isLoading: boolean;
  readonly isLoadingMore: boolean;
  readonly error: string | null;
  readonly hasMore: boolean;
  readonly nextCursor: string | null;
  readonly addPost: (post: Post) => void;
  readonly replacePost: (post: Post) => void;
  readonly updatePost: (postId: string, updater: (post: Post) => Post) => void;
  readonly refetch: (page?: number) => Promise<void>;
  readonly loadMore: () => Promise<void>;
}

/**
 * Fetches the authenticated user's feed from GET /api/feed.
 *
 * Combines posts from followed users + own posts, with reaction counts and
 * user reaction indicators from HU08. Provides optimistic `addPost` and
 * `replacePost` for client-side mutations (HU07 creation, HU09 reactions).
 *
 * Supports cursor-based infinite scroll via `loadMore`, as well as `refetch`.
 */
export function useFeed(): UseFeedReturn {
  const [posts, setPosts] = useState<readonly Post[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [isLoadingMore, setIsLoadingMore] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState<boolean>(false);

  const fetchFeed = useCallback(async (page: number = 1): Promise<void> => {
    setIsLoading(true);
    setError(null);

    try {
      const response = await postsApi.getFeed(page, 20);
      const mappedPosts = response.data.map((postApiResponse) =>
        mapPostApiResponseToPost(postApiResponse),
      );
      setPosts(mappedPosts);
      setNextCursor(response.meta.nextCursor ?? null);
      setHasMore(response.meta.hasMore ?? response.meta.hasNext);
    } catch (err) {
      setError("Failed to load feed");
      console.error("Feed fetch error:", err);
    } finally {
      setIsLoading(false);
    }
  }, []);

  const refetch = useCallback(
    async (page: number = 1): Promise<void> => {
      await fetchFeed(page);
    },
    [fetchFeed],
  );

  const loadMore = useCallback(async (): Promise<void> => {
    if (!hasMore || isLoadingMore || !nextCursor) {
      return;
    }
    setIsLoadingMore(true);
    try {
      const response = await postsApi.getFeed({
        cursor: nextCursor,
        limit: 20,
      });
      const mappedPosts = response.data.map((postApiResponse) =>
        mapPostApiResponseToPost(postApiResponse),
      );
      setPosts((prev) => {
        const existingIds = new Set(prev.map((p) => p.id));
        const newPosts = mappedPosts.filter((p) => !existingIds.has(p.id));
        return [...prev, ...newPosts];
      });
      setNextCursor(response.meta.nextCursor ?? null);
      setHasMore(response.meta.hasMore ?? response.meta.hasNext);
    } catch (err) {
      console.error("Feed loadMore error:", err);
    } finally {
      setIsLoadingMore(false);
    }
  }, [hasMore, isLoadingMore, nextCursor]);

  // Load feed on mount
  useEffect(() => {
    let ignore = false;

    const load = async () => {
      setIsLoading(true);
      setError(null);

      try {
        const response = await postsApi.getFeed(1, 20);
        if (!ignore) {
          const mappedPosts = response.data.map((postApiResponse) =>
            mapPostApiResponseToPost(postApiResponse),
          );
          setPosts(mappedPosts);
          setNextCursor(response.meta.nextCursor ?? null);
          setHasMore(response.meta.hasMore ?? response.meta.hasNext);
        }
      } catch (err) {
        if (!ignore) {
          setError("Failed to load feed");
          console.error("Feed fetch error:", err);
        }
      } finally {
        setIsLoading(false);
      }
    };

    void load();

    return () => {
      ignore = true;
    };
  }, []);

  const addPost = useCallback((post: Post) => {
    setPosts((prev) => [post, ...prev]);
  }, []);

  const replacePost = useCallback((post: Post) => {
    setPosts((prev) => prev.map((p) => (p.id === post.id ? post : p)));
  }, []);

  // Functional variant of replacePost: the updater receives the latest post
  // so concurrent changes (refetch, loadMore) are never overwritten by a
  // snapshot captured earlier. Keeps the previous array when the updater
  // returns the same reference, avoiding a needless re-render.
  const updatePost = useCallback(
    (postId: string, updater: (post: Post) => Post) => {
      setPosts((prev) => {
        let changed = false;
        const next = prev.map((p) => {
          if (p.id !== postId) return p;
          const updated = updater(p);
          if (updated !== p) changed = true;
          return updated;
        });
        return changed ? next : prev;
      });
    },
    [],
  );

  return {
    posts,
    isLoading,
    isLoadingMore,
    error,
    hasMore,
    nextCursor,
    addPost,
    replacePost,
    updatePost,
    refetch,
    loadMore,
  };
}
