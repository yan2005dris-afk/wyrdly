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
    readonly nextCursor?: string | null;
    readonly hasMore?: boolean;
  };
}

export interface GetFeedOptions {
  readonly page?: number;
  readonly pageSize?: number;
  readonly cursor?: string | null;
  readonly limit?: number;
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
    pageOrOptions?: number | GetFeedOptions,
    pageSizeArg?: number,
  ): Promise<FeedResponseDto> {
    let params: Record<string, unknown>;
    if (typeof pageOrOptions === "object" && pageOrOptions !== null) {
      params = {
        cursor: pageOrOptions.cursor ?? undefined,
        limit: pageOrOptions.limit ?? pageOrOptions.pageSize ?? 20,
        page: pageOrOptions.page,
        pageSize: pageOrOptions.pageSize,
      };
    } else {
      params = {
        page: pageOrOptions ?? 1,
        pageSize: pageSizeArg ?? 20,
      };
    }
    const response = await apiClient.get<FeedResponseDto>("/api/feed", {
      params,
    });
    return response.data;
  },
};
