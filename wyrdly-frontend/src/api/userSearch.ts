import { apiClient } from "./axios";
import type { UserSearchResponse } from "../types/userSearch";

export interface SearchUsersParams {
  readonly q: string;
  readonly page?: number;
  readonly pageSize?: number;
}

export const userSearchApi = {
  async searchUsers({
    q,
    page = 0,
    pageSize = 20,
  }: SearchUsersParams): Promise<UserSearchResponse> {
    const response = await apiClient.get<UserSearchResponse>("/api/users/search", {
      params: { q, page, pageSize },
    });
    return response.data;
  },
};