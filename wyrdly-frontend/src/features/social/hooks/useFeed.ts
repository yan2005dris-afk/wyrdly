import { useCallback, useState } from "react";
import type { Post } from "../../../types/feed";

interface UseFeedReturn {
  readonly posts: readonly Post[];
  readonly isLoading: boolean;
  readonly error: string | null;
  readonly addPost: (post: Post) => void;
  readonly replacePost: (post: Post) => void;
  readonly refetch: () => void;
}

/**
 * Holds the in-memory feed of posts shown on FeedPage.
 *
 * NOTE: There is no GET /api/posts endpoint yet — that lands with HU08.
 * Until then the feed starts empty and is populated by createPost results
 * (HU07) and the refetch is a no-op. When HU08 ships, replace `refetch` with
 * a real fetch and seed `posts` from the response.
 *
 * `replacePost` is a pragmatic helper used by the optimistic reaction UI on
 * FeedPage until HU09 ships the real reaction mutation hook. It is not part
 * of the published spec signature but keeps the previous PR's behavior intact.
 */
export function useFeed(): UseFeedReturn {
  const [posts, setPosts] = useState<readonly Post[]>([]);
  const [isLoading] = useState<boolean>(false);
  const [error] = useState<string | null>(null);

  const addPost = useCallback((post: Post) => {
    setPosts((prev) => [post, ...prev]);
  }, []);

  const replacePost = useCallback((post: Post) => {
    setPosts((prev) => prev.map((p) => (p.id === post.id ? post : p)));
  }, []);

  const refetch = useCallback(() => {
    // Intentionally empty — GET /api/posts ships with HU08.
  }, []);

  return { posts, isLoading, error, addPost, replacePost, refetch };
}
