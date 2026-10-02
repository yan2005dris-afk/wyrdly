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
  const [isLoading, setIsLoading] = useState<boolean>(false);

  const logout = useCallback(() => {
    authApi.logout().catch(() => {});
    setAccessToken(null);
    setToken(null);
    setUser(null);
    localStorage.removeItem("wyrdly_user");
  }, []);

  useEffect(() => {
    const initAuth = async () => {
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
