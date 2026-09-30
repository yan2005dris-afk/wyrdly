import { useState, useEffect, useCallback } from "react";
import { usersApi } from "../../../api/users";
import type {
  GetSuggestionsParams,
  GraphSuggestionUser,
} from "../../../types/suggestions";

interface UseGraphSuggestionsReturn {
  readonly suggestions: readonly GraphSuggestionUser[];
  readonly isLoading: boolean;
  readonly error: string | null;
  readonly refetch: () => void;
}

export function useGraphSuggestions(
  params?: GetSuggestionsParams,
): UseGraphSuggestionsReturn {
  const [suggestions, setSuggestions] = useState<
    readonly GraphSuggestionUser[]
  >([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const fetchSuggestions = useCallback(async () => {
    setIsLoading(true);
    setError(null);

    try {
      const response = await usersApi.getSuggestions(params);
      setSuggestions(response.data);
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "Failed to load suggestions";
      setError(message);
      setSuggestions([]);
    } finally {
      setIsLoading(false);
    }
  }, [params]);

  useEffect(() => {
    let isCancelled = false;

    const load = async () => {
      try {
        const response = await usersApi.getSuggestions(params);
        if (!isCancelled) {
          setSuggestions(response.data);
          setError(null);
        }
      } catch (err) {
        if (!isCancelled) {
          const message =
            err instanceof Error ? err.message : "Failed to load suggestions";
          setError(message);
          setSuggestions([]);
        }
      } finally {
        if (!isCancelled) {
          setIsLoading(false);
        }
      }
    };

    void load();

    return () => {
      isCancelled = true;
    };
  }, [params]);

  return {
    suggestions,
    isLoading,
    error,
    refetch: fetchSuggestions,
  };
}
