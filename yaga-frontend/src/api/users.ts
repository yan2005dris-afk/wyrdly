import { apiClient } from "./axios";

export interface UserProfileApiResponse {
  readonly id: string;
  readonly username: string;
  readonly fullName: string;
  readonly bio: string | null;
  readonly avatarUrl: string | null;
  readonly followersCount: number;
  readonly followingCount: number;
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
};
