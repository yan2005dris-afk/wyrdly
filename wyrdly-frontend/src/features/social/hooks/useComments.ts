// TODO(hu-migrate-social-to-tanstack): see ADR-007
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { commentsApi } from "../../../api/commentsApi";
import type { CommentListResponse } from "../../../types/comments";

export interface UseCommentsResult {
  readonly comments: readonly CommentListResponse["data"][number][];
  readonly totalCount: number;
  readonly isLoading: boolean;
  readonly isError: boolean;
  readonly error: Error | null;
  readonly createComment: (
    content: string,
  ) => Promise<CommentListResponse["data"][number]>;
  readonly isCreating: boolean;
  readonly deleteComment: (commentId: string) => Promise<void>;
  readonly isDeleting: boolean;
}

export function useComments(
  postId: string,
  isEnabled = true,
): UseCommentsResult {
  const queryClient = useQueryClient();

  const query = useQuery({
    queryKey: ["comments", postId],
    queryFn: () => commentsApi.getComments(postId),
    enabled: isEnabled && Boolean(postId),
  });

  const createMutation = useMutation({
    mutationFn: (content: string) => commentsApi.createComment(postId, content),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["comments", postId] });
      // Also invalidate feed so commentsCount updates
      queryClient.invalidateQueries({ queryKey: ["feed"] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (commentId: string) =>
      commentsApi.deleteComment(postId, commentId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["comments", postId] });
      queryClient.invalidateQueries({ queryKey: ["feed"] });
    },
  });

  return {
    comments: query.data?.data ?? [],
    totalCount: query.data?.totalCount ?? 0,
    isLoading: query.isLoading,
    isError: query.isError,
    error: query.error ?? null,
    createComment: createMutation.mutateAsync,
    isCreating: createMutation.isPending,
    deleteComment: deleteMutation.mutateAsync,
    isDeleting: deleteMutation.isPending,
  };
}
