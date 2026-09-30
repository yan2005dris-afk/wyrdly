import { useCallback, useEffect, useState } from "react";
import { postsApi } from "../api/posts";
import type { Post } from "../types/feed";
import { mapPostApiResponseToPost } from "../types/feed";

interface UseFeedReturn {
  readonly posts: readonly Post[];
  readonly isLoading: boolean;
  readonly error: string | null;
  readonly addPost: (post: Post) => void;
  readonly replacePost: (post: Post) => void;
  readonly refetch: () => Promise<void>;
}

/**
 * Fetches the authenticated user's feed from GET /api/feed.
 *
 * Combines posts from followed users + own posts, with reaction counts and
 * user reaction indicators from HU08. Provides optimistic `addPost` and
 * `replacePost` for client-side mutations (HU07 creation, HU09 reactions).
 *
 * Loads feed on mount and provides `refetch` to reload with pagination.
 */
export function useFeed(): UseFeedReturn {
  const [posts, setPosts] = useState<readonly Post[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [currentPage, setCurrentPage] = useState(1);

  const refetch = useCallback(async (page: number = 1): Promise<void> => {
    setIsLoading(true);
    setError(null);

    try {
      const response = await postsApi.getFeed(page, 20);
      const mappedPosts = response.data.map((postApiResponse) =>
        mapPostApiResponseToPost(postApiResponse),
      );
      setPosts(mappedPosts);
      setCurrentPage(page);
    } catch (err) {
      setError("Failed to load feed");
      console.error("Feed fetch error:", err);
    } finally {
      setIsLoading(false);
    }
  }, []);

  // Load feed on mount
  useEffect(() => {
    refetch();
  }, [refetch]);

  const addPost = useCallback((post: Post) => {
    setPosts((prev) => [post, ...prev]);
  }, []);

  const replacePost = useCallback((post: Post) => {
    setPosts((prev) => prev.map((p) => (p.id === post.id ? post : p)));
  }, []);

  return { posts, isLoading, error, addPost, replacePost, refetch };
}
