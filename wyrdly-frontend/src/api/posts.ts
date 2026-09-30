import { apiClient } from "./axios";
import type { CreatePostApiPayload, PostApiResponse } from "../types/feed";

export interface FeedResponseDto {
  readonly data: readonly PostApiResponse[];
  readonly meta: {
    readonly page: number;
    readonly pageSize: number;
    readonly totalElements: number;
    readonly totalPages: number;
    readonly hasNext: boolean;
  };
}

export const postsApi = {
  async create(payload: CreatePostApiPayload): Promise<PostApiResponse> {
    const response = await apiClient.post<PostApiResponse>("/api/posts", {
      content: payload.content,
      mediaUrl: payload.mediaUrl ?? null,
    });
    return response.data;
  },

  async getFeed(
    page: number = 1,
    pageSize: number = 20,
  ): Promise<FeedResponseDto> {
    const response = await apiClient.get<FeedResponseDto>("/api/feed", {
      params: { page, pageSize },
    });
    return response.data;
  },
};
