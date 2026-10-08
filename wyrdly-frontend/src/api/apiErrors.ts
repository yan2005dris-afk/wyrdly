import axios, { type AxiosError } from "axios";

export interface ApiErrorPayload {
  readonly status?: number;
  readonly error?: string;
  readonly message?: string;
  readonly code?: string;
  readonly violations?: readonly string[];
  readonly timestamp?: string;
}

export interface ApiErrorDetails {
  readonly statusCode: number | null;
  readonly code: string | null;
  readonly message: string;
  readonly violations: readonly string[];
}

const STATUS_FALLBACKS: Record<number, string> = {
  400: "Invalid request. Please check the entered data.",
  401: "Authentication required or session expired. Please sign in again.",
  403: "Access denied. You do not have permission for this action.",
  404: "Requested resource was not found.",
  409: "Conflict detected. The username, email, or resource already exists.",
  500: "Internal server error. Please try again later.",
  502: "Bad gateway. The backend server is currently unreachable.",
  503: "Service temporarily unavailable. The system is busy or degraded, please retry shortly.",
};

/**
 * Normalizes any error (AxiosError, Error, or unknown) into a structured ApiErrorDetails.
 * Accurately extracts backend messages, violation arrays, and provides semantic fallbacks
 * for 400, 401, 403, 404, 409, 500, 502, and 503 HTTP status codes.
 */
export function extractApiError(error: unknown): ApiErrorDetails {
  if (axios.isAxiosError(error)) {
    const axiosErr = error as AxiosError<ApiErrorPayload | string>;
    const statusCode = axiosErr.response?.status ?? null;
    const data = axiosErr.response?.data;

    let backendMessage: string | null = null;
    let backendCode: string | null = null;
    let violations: readonly string[] = [];

    if (data && typeof data === "object") {
      backendMessage = data.message || data.error || null;
      backendCode = data.code || null;
      if (Array.isArray(data.violations)) {
        violations = data.violations.map(String);
      }
    } else if (typeof data === "string" && data.trim()) {
      backendMessage = data;
    }

    // If backend provided validation violations, format them into the message
    if (violations.length > 0 && !backendMessage) {
      backendMessage = violations.join(". ");
    }

    const fallback =
      (statusCode ? STATUS_FALLBACKS[statusCode] : null) ||
      (axiosErr.code === "ECONNABORTED"
        ? "Request timed out. Please try again."
        : !axiosErr.response
          ? "Network connection error. Please check your internet connection."
          : axiosErr.message || "An unexpected error occurred.");

    return {
      statusCode,
      code: backendCode,
      message: backendMessage || fallback,
      violations,
    };
  }

  if (error instanceof Error) {
    return {
      statusCode: null,
      code: null,
      message: error.message || "An unexpected error occurred.",
      violations: [],
    };
  }

  return {
    statusCode: null,
    code: null,
    message: typeof error === "string" ? error : "An unknown error occurred.",
    violations: [],
  };
}

/**
 * Returns a user-friendly string message from any error.
 */
export function getApiErrorMessage(error: unknown): string {
  return extractApiError(error).message;
}
