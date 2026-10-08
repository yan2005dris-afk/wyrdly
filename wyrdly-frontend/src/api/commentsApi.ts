import { apiClient } from "./axios";
import type { Comment, CommentListResponse } from "../types/comments";

export const commentsApi = {
  async getComments(
    postId: string,
    page = 1,
    pageSize = 20,
  ): Promise<CommentListResponse> {
    const res = await apiClient.get<CommentListResponse>(
      `/api/posts/${encodeURIComponent(postId)}/comments?page=${page}&pageSize=${pageSize}`,
    );
    return res.data;
  },

  async createComment(postId: string, content: string): Promise<Comment> {
    const res = await apiClient.post<Comment>(
      `/api/posts/${encodeURIComponent(postId)}/comments`,
      { content },
    );
    return res.data;
  },

  async deleteComment(postId: string, commentId: string): Promise<void> {
    await apiClient.delete(
      `/api/posts/${encodeURIComponent(postId)}/comments/${encodeURIComponent(commentId)}`,
    );
  },
};
