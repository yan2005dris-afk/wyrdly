import { apiClient } from "./axios";
import type {
  AuthResponse,
  LoginCredentials,
  RegisterCredentials,
  User,
} from "../types/auth";

export const authApi = {
  async register(credentials: RegisterCredentials): Promise<AuthResponse> {
    const response = await apiClient.post<AuthResponse>(
      "/api/auth/register",
      credentials,
    );
    return response.data;
  },

  async login(credentials: LoginCredentials): Promise<AuthResponse> {
    const response = await apiClient.post<AuthResponse>(
      "/api/auth/login",
      credentials,
    );
    return response.data;
  },

  async refresh(refreshToken?: string): Promise<AuthResponse> {
    const response = await apiClient.post<AuthResponse>(
      "/api/auth/refresh",
      refreshToken ? { refreshToken } : {},
    );
    return response.data;
  },

  async logout(): Promise<void> {
    await apiClient.post("/api/auth/logout");
  },

  async getMe(): Promise<User> {
    const response = await apiClient.get<User>("/api/auth/me");
    return response.data;
  },
};
