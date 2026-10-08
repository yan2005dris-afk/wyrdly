import { apiClient } from "./axios";
import type {
  CreatePostApiPayload,
  PostApiResponse,
  ReactionType,
} from "../types/feed";

// Re-export ReactionType for consumers of the posts module that prefer a
// single import surface. The canonical definition lives in src/types/feed.ts.
export type { ReactionType } from "../types/feed";

/**
 * Outcome of a single POST /api/posts/{postId}/react call.
 * Mirrors the backend ReactionResult record (HU09).
 *
 * - ADDED   → relationship was created (no previous reaction).
 * - REMOVED → relationship was deleted (same type toggled twice).
 * - UPDATED → relationship type was changed (different type submitted).
 */
export type ReactionStatus = "ADDED" | "REMOVED" | "UPDATED";

export interface ReactPostResponse {
  readonly status: ReactionStatus;
  /** Type of the user's reaction AFTER the operation. null when removed. */
  readonly reactionType: ReactionType | null;
  /**
   * Total count of reactions for the post after the operation.
   * Note: backend reports the aggregated total across all types (HU08).
   */
  readonly totalReactions: number;
}

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

  async getById(postId: string): Promise<PostApiResponse> {
    const response = await apiClient.get<PostApiResponse>(
      `/api/posts/${postId}`,
    );
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

  /**
   * HU09: register a reaction on a post. The backend treats this as a toggle:
   * - No previous reaction → ADDED
   * - Same type as current   → REMOVED
   * - Different type          → UPDATED
   *
   * `signal` allows callers (e.g. useReaction) to cancel in-flight requests
   * when a newer click arrives for the same post. `Idempotency-Key` is sent
   * on every call so the backend Redis dedup window (250ms) protects against
   * double-clicks.
   */
  react: async (
    postId: string,
    type: ReactionType,
    signal?: AbortSignal,
  ): Promise<ReactPostResponse> => {
    const { data } = await apiClient.post<ReactPostResponse>(
      `/api/posts/${postId}/react`,
      { type },
      {
        signal,
        headers: { "Idempotency-Key": crypto.randomUUID() },
      },
    );
    return data;
  },
};
