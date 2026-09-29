import { apiClient } from "./axios";
import type { CreatePostApiPayload, PostApiResponse } from "../types/feed";

export const postsApi = {
  async create(payload: CreatePostApiPayload): Promise<PostApiResponse> {
    const response = await apiClient.post<PostApiResponse>("/api/posts", {
      content: payload.content,
      mediaUrl: payload.mediaUrl ?? null,
    });
    return response.data;
  },
};
