import axios, { type InternalAxiosRequestConfig } from "axios";
import { getAccessToken } from "./tokenStore";

const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL ??
  (import.meta.env.DEV ? "http://localhost:8080" : "");

/**
 * Dedicated axios instance for multipart/form-data uploads.
 *
 * The main `apiClient` (see ./axios) sets a global
 * `Content-Type: application/json` header. That collides with
 * FormData uploads: depending on axios internals and the auth
 * interceptor, the Content-Type can survive into the outgoing request
 * and the backend's `@Consumes(MULTIPART_FORM_DATA)` then answers
 * with 415 Unsupported Media Type.
 *
 * This client deliberately omits any default Content-Type so axios
 * can set the correct `multipart/form-data; boundary=...` itself when
 * the body is a FormData instance.
 */
export const mediaClient = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
});

mediaClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getAccessToken();
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },

  (error) => Promise.reject(error),
);
