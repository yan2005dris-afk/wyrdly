import {
  useState,
  useEffect,
  useCallback,
  useMemo,
  type FC,
  type ReactNode,
} from "react";
import { authApi } from "../api/authApi";
import { setAccessToken } from "../../../api/tokenStore";
import type {
  User,
  LoginCredentials,
  RegisterCredentials,
  AuthContextType,
} from "../types";
import { AuthContext } from "./authContextInstance";

export const AuthProvider: FC<{ children: ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(() => {
    const savedUser = localStorage.getItem("wyrdly_user");
    if (savedUser) {
      try {
        return JSON.parse(savedUser) as User;
      } catch {
        return null;
      }
    }
    return null;
  });
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(
    () => !!localStorage.getItem("wyrdly_user"),
  );

  const logout = useCallback(() => {
    authApi.logout().catch(() => {});
    setAccessToken(null);
    setToken(null);
    setUser(null);
    localStorage.removeItem("wyrdly_user");
  }, []);

  useEffect(() => {
    const initAuth = async () => {
      const savedUser = localStorage.getItem("wyrdly_user");
      if (!savedUser) {
        setIsLoading(false);
        return;
      }

      try {
        const res = await authApi.refresh();
        setAccessToken(res.token);
        setToken(res.token);
        setUser(res.user);
        localStorage.setItem("wyrdly_user", JSON.stringify(res.user));
      } catch {
        setAccessToken(null);
        setToken(null);
        setUser(null);
        localStorage.removeItem("wyrdly_user");
      } finally {
        setIsLoading(false);
      }
    };

    void initAuth();
  }, []);

  const login = useCallback(async (credentials: LoginCredentials) => {
    setIsLoading(true);
    try {
      const response = await authApi.login(credentials);
      setAccessToken(response.token);
      setToken(response.token);
      setUser(response.user);
      localStorage.setItem("wyrdly_user", JSON.stringify(response.user));
    } finally {
      setIsLoading(false);
    }
  }, []);

  const register = useCallback(async (credentials: RegisterCredentials) => {
    setIsLoading(true);
    try {
      const response = await authApi.register(credentials);
      setAccessToken(response.token);
      setToken(response.token);
      setUser(response.user);
      localStorage.setItem("wyrdly_user", JSON.stringify(response.user));
    } finally {
      setIsLoading(false);
    }
  }, []);

  const updateUser = useCallback((updates: Partial<User>) => {
    setUser((prev) => {
      if (!prev) return prev;
      const next = { ...prev, ...updates };
      localStorage.setItem("wyrdly_user", JSON.stringify(next));
      return next;
    });
  }, []);

  const contextValue = useMemo<AuthContextType>(
    () => ({
      user,
      token,
      isAuthenticated: !!token && !!user,
      isLoading,
      login,
      register,
      logout,
      updateUser,
    }),
    [user, token, isLoading, login, register, logout, updateUser],
  );

  return (
    <AuthContext.Provider value={contextValue}>{children}</AuthContext.Provider>
  );
};
