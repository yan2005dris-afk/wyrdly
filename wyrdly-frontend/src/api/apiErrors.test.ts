import { describe, expect, it } from "vitest";
import { AxiosError, AxiosHeaders, type AxiosResponse } from "axios";
import { extractApiError, getApiErrorMessage } from "./apiErrors";

const createAxiosError = (
  status: number | undefined,
  data?: unknown,
  code?: string,
) => {
  const headers = new AxiosHeaders();
  const config = { headers };
  const response = status
    ? {
        status,
        statusText: "Error",
        headers: {},
        config,
        data,
      }
    : undefined;

  const err = new AxiosError(
    "Request failed",
    code,
    config,
    {},
    response as unknown as AxiosResponse,
  );
  return err;
};

describe("apiErrors module", () => {
  it("extracts backend message and status from 400 Bad Request with violations", () => {
    const err = createAxiosError(400, {
      status: 400,
      violations: [
        "Username must not be empty",
        "Bio must be at most 160 characters",
      ],
    });

    const result = extractApiError(err);
    expect(result.statusCode).toBe(400);
    expect(result.violations).toHaveLength(2);
    expect(result.message).toContain("Username must not be empty");
    expect(result.message).toContain("Bio must be at most 160 characters");
  });

  it("extracts 409 Conflict message when user already exists", () => {
    const err = createAxiosError(409, {
      status: 409,
      error: "Conflict",
      message: "User with username 'alice' already exists",
    });

    const result = extractApiError(err);
    expect(result.statusCode).toBe(409);
    expect(result.message).toBe("User with username 'alice' already exists");
  });

  it("falls back to semantic message when 403 Forbidden has no custom message", () => {
    const err = createAxiosError(403);
    const result = extractApiError(err);

    expect(result.statusCode).toBe(403);
    expect(result.message).toContain("Access denied");
  });

  it("extracts 503 Service Unavailable circuit breaker message", () => {
    const err = createAxiosError(503, {
      status: 503,
      code: "DEPENDENCY_DOWN",
      message:
        "Servicio temporalmente no disponible. Reintenta en unos segundos.",
    });

    const result = extractApiError(err);
    expect(result.statusCode).toBe(503);
    expect(result.code).toBe("DEPENDENCY_DOWN");
    expect(result.message).toBe(
      "Servicio temporalmente no disponible. Reintenta en unos segundos.",
    );
  });

  it("falls back to semantic 502 Bad Gateway message", () => {
    const err = createAxiosError(502);
    const result = extractApiError(err);

    expect(result.statusCode).toBe(502);
    expect(result.message).toContain("Bad gateway");
  });

  it("handles network connection error when no response is received", () => {
    const err = createAxiosError(undefined);
    const result = extractApiError(err);

    expect(result.statusCode).toBeNull();
    expect(result.message).toContain("Network connection error");
  });

  it("handles request timeout (ECONNABORTED)", () => {
    const err = createAxiosError(undefined, undefined, "ECONNABORTED");
    const result = extractApiError(err);

    expect(result.message).toContain("Request timed out");
  });

  it("handles standard Error instances and unknown objects", () => {
    const standardErr = new Error("Something broke");
    expect(getApiErrorMessage(standardErr)).toBe("Something broke");

    expect(getApiErrorMessage("Direct error string")).toBe(
      "Direct error string",
    );
    expect(getApiErrorMessage(12345)).toBe("An unknown error occurred.");
  });
});
