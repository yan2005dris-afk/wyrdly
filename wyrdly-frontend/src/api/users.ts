import { apiClient } from "./axios";
import type {
  FollowActionResponse,
  GetSuggestionsParams,
  GraphSuggestionsResponse,
} from "../types/suggestions";

export interface UserProfileApiResponse {
  readonly id: string;
  readonly username: string;
  readonly fullName: string;
  readonly bio: string | null;
  readonly avatarUrl: string | null;
  readonly followersCount: number;
  readonly followingCount: number;
  readonly postsCount: number;
  readonly isFollowing: boolean;
  readonly createdAt: string;
}

export interface UpdateProfilePayload {
  readonly fullName?: string;
  readonly bio?: string;
  readonly avatarUrl?: string;
}

export const usersApi = {
  async getProfile(username: string): Promise<UserProfileApiResponse> {
    const response = await apiClient.get<UserProfileApiResponse>(
      `/api/users/${encodeURIComponent(username)}`,
    );
    return response.data;
  },

  async updateProfile(
    payload: UpdateProfilePayload,
  ): Promise<UserProfileApiResponse> {
    const response = await apiClient.put<UserProfileApiResponse>(
      "/api/users/profile",
      payload,
    );
    return response.data;
  },

  async getSuggestions(
    params: GetSuggestionsParams = {},
  ): Promise<GraphSuggestionsResponse> {
    const response = await apiClient.get<GraphSuggestionsResponse>(
      "/api/users/suggestions",
      {
        params: {
          page: params.page ?? 0,
          pageSize: params.pageSize ?? 10,
        },
      },
    );
    return response.data;
  },

  async follow(targetUserId: string): Promise<FollowActionResponse> {
    const response = await apiClient.post<FollowActionResponse>(
      `/api/users/${encodeURIComponent(targetUserId)}/follow`,
    );
    return response.data;
  },

  async unfollow(targetUserId: string): Promise<FollowActionResponse> {
    const response = await apiClient.delete<FollowActionResponse>(
      `/api/users/${encodeURIComponent(targetUserId)}/follow`,
    );
    return response.data;
  },
};
