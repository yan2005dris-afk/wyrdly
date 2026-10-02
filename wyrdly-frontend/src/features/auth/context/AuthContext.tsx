import {
  useState,
  useEffect,
  useCallback,
  useMemo,
  type FC,
  type ReactNode,
} from "react";
import { authApi } from "../api/authApi";
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

  const [token, setToken] = useState<string | null>(() =>
    localStorage.getItem("wyrdly_token"),
  );

  const [isLoading, setIsLoading] = useState<boolean>(
    () => !!localStorage.getItem("wyrdly_token"),
  );

  const logout = useCallback(() => {
    authApi.logout().catch(() => {});
    localStorage.removeItem("wyrdly_token");
    localStorage.removeItem("wyrdly_refresh_token");
    localStorage.removeItem("wyrdly_user");
    setToken(null);
    setUser(null);
  }, []);

  useEffect(() => {
    const initAuth = async () => {
      const storedToken = localStorage.getItem("wyrdly_token");
      if (storedToken) {
        try {
          const currentUser = await authApi.getMe();
          setUser(currentUser);
          localStorage.setItem("wyrdly_user", JSON.stringify(currentUser));
        } catch {
          try {
            const res = await authApi.refresh();
            localStorage.setItem("wyrdly_token", res.token);
            localStorage.setItem("wyrdly_user", JSON.stringify(res.user));
            setToken(res.token);
            setUser(res.user);
          } catch {
            logout();
          }
        } finally {
          setIsLoading(false);
        }
      }
    };

    void initAuth();
  }, [logout]);

  const login = useCallback(async (credentials: LoginCredentials) => {
    setIsLoading(true);
    try {
      const response = await authApi.login(credentials);
      localStorage.setItem("wyrdly_token", response.token);
      localStorage.setItem("wyrdly_user", JSON.stringify(response.user));
      setToken(response.token);
      setUser(response.user);
    } finally {
      setIsLoading(false);
    }
  }, []);

  const register = useCallback(async (credentials: RegisterCredentials) => {
    setIsLoading(true);
    try {
      const response = await authApi.register(credentials);
      localStorage.setItem("wyrdly_token", response.token);
      localStorage.setItem("wyrdly_user", JSON.stringify(response.user));
      setToken(response.token);
      setUser(response.user);
    } finally {
      setIsLoading(false);
    }
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
    }),
    [user, token, isLoading, login, register, logout],
  );

  return (
    <AuthContext.Provider value={contextValue}>{children}</AuthContext.Provider>
  );
};
