import { useCallback, useState } from "react";
import { postsApi } from "../../../api/posts";
import type {
  CreatePostApiPayload,
  PostApiResponse,
} from "../../../types/feed";

interface UseCreatePostReturn {
  readonly createPost: (
    payload: CreatePostApiPayload,
  ) => Promise<PostApiResponse | null>;
  readonly isSubmitting: boolean;
  readonly error: string | null;
}

const FAILURE_MESSAGE = "Failed to publish post";

export function useCreatePost(): UseCreatePostReturn {
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const createPost = useCallback(
    async (payload: CreatePostApiPayload): Promise<PostApiResponse | null> => {
      setIsSubmitting(true);
      setError(null);

      try {
        const response = await postsApi.create(payload);
        return response;
      } catch {
        setError(FAILURE_MESSAGE);
        return null;
      } finally {
        setIsSubmitting(false);
      }
    },
    [],
  );

  return { createPost, isSubmitting, error };
}
