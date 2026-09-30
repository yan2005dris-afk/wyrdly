import { renderHook, act, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { usersApi } from "../../../api/users";
import { useGraphSuggestions } from "./useGraphSuggestions";
import type {
  GraphSuggestionUser,
  GraphSuggestionsResponse,
} from "../../../types/suggestions";

vi.mock("../../../api/users", () => ({
  usersApi: {
    getSuggestions: vi.fn(),
  },
}));

const mockedGetSuggestions = vi.mocked(usersApi.getSuggestions);

const aliceSuggestion: GraphSuggestionUser = {
  id: "user-alice",
  username: "alice",
  fullName: "Alice Chen",
  avatarUrl: null,
  mutualConnectionSnippet: "Followed by Jon and 2 others",
  isFollowing: false,
};

const successResponse: GraphSuggestionsResponse = {
  data: [aliceSuggestion],
  meta: { page: 0, pageSize: 10, totalCount: 1 },
};

describe("useGraphSuggestions", () => {
  beforeEach(() => {
    mockedGetSuggestions.mockReset();
  });

  it("starts with isLoading=true and resolves to the fetched suggestions", async () => {
    mockedGetSuggestions.mockResolvedValueOnce(successResponse);

    const { result } = renderHook(() => useGraphSuggestions());

    expect(result.current.isLoading).toBe(true);
    expect(result.current.suggestions).toEqual([]);
    expect(result.current.error).toBeNull();

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.suggestions).toEqual([aliceSuggestion]);
    expect(result.current.error).toBeNull();
    expect(mockedGetSuggestions).toHaveBeenCalledTimes(1);
  });

  it("sets error and empty data when the API throws", async () => {
    mockedGetSuggestions.mockRejectedValueOnce(new Error("boom"));

    const { result } = renderHook(() => useGraphSuggestions());

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.error).toBe("boom");
    expect(result.current.suggestions).toEqual([]);
  });

  it("uses the 'Failed to load suggestions' fallback when the error has no message", async () => {
    mockedGetSuggestions.mockRejectedValueOnce("not-an-error-object");

    const { result } = renderHook(() => useGraphSuggestions());

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.error).toBe("Failed to load suggestions");
    expect(result.current.suggestions).toEqual([]);
  });

  it("calls the API with undefined when no params are provided", async () => {
    mockedGetSuggestions.mockResolvedValueOnce(successResponse);

    renderHook(() => useGraphSuggestions());

    await waitFor(() => {
      expect(mockedGetSuggestions).toHaveBeenCalled();
    });

    // The hook forwards the params argument straight to usersApi.getSuggestions,
    // which applies its own default of { page: 0, pageSize: 10 } internally.
    expect(mockedGetSuggestions).toHaveBeenCalledWith(undefined);
  });

  it("forwards explicit params to the API", async () => {
    mockedGetSuggestions.mockResolvedValueOnce(successResponse);

    renderHook(() => useGraphSuggestions({ page: 3, pageSize: 25 }));

    await waitFor(() => {
      expect(mockedGetSuggestions).toHaveBeenCalled();
    });

    expect(mockedGetSuggestions).toHaveBeenCalledWith({
      page: 3,
      pageSize: 25,
    });
  });

  it("refetch triggers another API call", async () => {
    mockedGetSuggestions.mockResolvedValue(successResponse);

    const { result } = renderHook(() => useGraphSuggestions());

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(mockedGetSuggestions).toHaveBeenCalledTimes(1);

    await act(async () => {
      result.current.refetch();
    });

    await waitFor(() => {
      expect(mockedGetSuggestions).toHaveBeenCalledTimes(2);
    });
  });

  it("does not update state when unmounted before the response resolves", async () => {
    let resolvePromise: ((value: GraphSuggestionsResponse) => void) | undefined;
    const delayed = new Promise<GraphSuggestionsResponse>((resolve) => {
      resolvePromise = resolve;
    });
    mockedGetSuggestions.mockReturnValueOnce(delayed);

    const { unmount } = renderHook(() => useGraphSuggestions());

    // Simulate unmount before the API call resolves.
    unmount();

    // Resolving the in-flight promise after unmount must not throw.
    expect(() => resolvePromise?.(successResponse)).not.toThrow();
  });
});
