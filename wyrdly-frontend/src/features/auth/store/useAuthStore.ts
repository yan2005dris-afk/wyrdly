import { create } from "zustand";
import { authApi } from "../api/authApi";
import { setAccessToken } from "../../../api/tokenStore";
import type { User, LoginCredentials, RegisterCredentials } from "../types";

export interface AuthState {
  readonly user: User | null;
  readonly token: string | null;
  readonly isAuthenticated: boolean;
  readonly isLoading: boolean;
  readonly login: (credentials: LoginCredentials) => Promise<void>;
  readonly register: (credentials: RegisterCredentials) => Promise<void>;
  readonly logout: () => void;
  readonly initAuth: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  token: null,
  isAuthenticated: false,
  isLoading: false,
  initAuth: async () => {
    const savedUser = localStorage.getItem("wyrdly_user");
    if (!savedUser) {
      set({ isLoading: false, isAuthenticated: false });
      return;
    }

    try {
      const res = await authApi.refresh();
      setAccessToken(res.token);
      localStorage.setItem("wyrdly_user", JSON.stringify(res.user));
      set({
        user: res.user,
        token: res.token,
        isAuthenticated: true,
        isLoading: false,
      });
    } catch {
      setAccessToken(null);
      localStorage.removeItem("wyrdly_user");
      set({
        user: null,
        token: null,
        isAuthenticated: false,
        isLoading: false,
      });
    }
  },

  login: async (credentials: LoginCredentials) => {
    set({ isLoading: true });
    try {
      const response = await authApi.login(credentials);
      setAccessToken(response.token);
      localStorage.setItem("wyrdly_user", JSON.stringify(response.user));
      set({
        user: response.user,
        token: response.token,
        isAuthenticated: true,
        isLoading: false,
      });
    } catch (err) {
      set({ isLoading: false });
      throw err;
    }
  },

  register: async (credentials: RegisterCredentials) => {
    set({ isLoading: true });
    try {
      const response = await authApi.register(credentials);
      setAccessToken(response.token);
      localStorage.setItem("wyrdly_user", JSON.stringify(response.user));
      set({
        user: response.user,
        token: response.token,
        isAuthenticated: true,
        isLoading: false,
      });
    } catch (err) {
      set({ isLoading: false });
      throw err;
    }
  },

  logout: () => {
    authApi.logout().catch(() => {});
    setAccessToken(null);
    localStorage.removeItem("wyrdly_user");
    set({
      user: null,
      token: null,
      isAuthenticated: false,
      isLoading: false,
    });
  },
}));
