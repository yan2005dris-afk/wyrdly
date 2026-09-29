import { useEffect, useState, useCallback, type FC, type FormEvent } from "react";
import { useSearchParams } from "react-router-dom";
import { Search } from "lucide-react";
import { userSearchApi } from "../api/userSearch";
import type { UserSearchResponse } from "../types/userSearch";
import { Input } from "../components/ui/Input";
import { UserSearchResultCard } from "../components/social";

const MIN_QUERY_LENGTH = 2;
const DEBOUNCE_MS = 300;

export const ExplorePage: FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const urlQuery = searchParams.get("q") ?? "";
  const [draftQuery, setDraftQuery] = useState(urlQuery);
  const [response, setResponse] = useState<UserSearchResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setDraftQuery(urlQuery);
  }, [urlQuery]);

  useEffect(() => {
    const trimmed = urlQuery.trim();

    if (trimmed.length < MIN_QUERY_LENGTH) {
      setResponse(null);
      setError(null);
      setLoading(false);
      return;
    }

    let cancelled = false;
    setLoading(true);
    setError(null);

    const timeout = setTimeout(() => {
      userSearchApi
        .searchUsers({ q: trimmed })
        .then((data) => {
          if (!cancelled) {
            setResponse(data);
            setLoading(false);
          }
        })
        .catch((err: unknown) => {
          if (!cancelled) {
            const message =
              err && typeof err === "object" && "response" in err
                ? (err as { response?: { data?: { message?: string } } })
                    .response?.data?.message
                : null;
            setError(message ?? "No se pudo completar la búsqueda.");
            setResponse(null);
            setLoading(false);
          }
        });
    }, DEBOUNCE_MS);

    return () => {
      cancelled = true;
      clearTimeout(timeout);
    };
  }, [urlQuery]);

  const handleSubmit = useCallback(
    (event: FormEvent) => {
      event.preventDefault();
      const next = draftQuery.trim();
      if (next === urlQuery.trim()) {
        return;
      }
      setSearchParams(
        next ? { q: next } : {},
        { replace: false },
      );
    },
    [draftQuery, urlQuery, setSearchParams],
  );

  const handleFollowToggle = useCallback((userId: string) => {
    setResponse((prev) => {
      if (!prev) return prev;
      return {
        ...prev,
        data: prev.data.map((u) =>
          u.id === userId ? { ...u, isFollowing: !u.isFollowing } : u,
        ),
      };
    });
  }, []);

  const trimmed = urlQuery.trim();
  const showEmpty = trimmed.length >= MIN_QUERY_LENGTH && !loading && !error && response && response.data.length === 0;
  const showMinHint = trimmed.length === 1;

  return (
    <div
      className="flex flex-col gap-6 w-full"
      data-testid="explore-page"
    >
      <header className="flex flex-col gap-1">
        <h1
          className="text-2xl font-semibold text-slate-900"
          data-testid="explore-title"
        >
          Explorar
        </h1>
        <p className="text-sm text-slate-500">
          Busca personas por nombre, usuario o bio.
        </p>
      </header>

      <form onSubmit={handleSubmit} className="w-full max-w-2xl">
        <Input
          variant="pill"
          placeholder="Buscar personas…"
          value={draftQuery}
          onChange={(e) => setDraftQuery(e.target.value)}
          leftIcon={<Search className="w-4 h-4 text-slate-400" />}
          autoFocus
          data-testid="explore-search-input"
        />
      </form>

      {showMinHint && (
        <p
          className="text-sm text-amber-600"
          data-testid="explore-min-hint"
        >
          Escribe al menos {MIN_QUERY_LENGTH} caracteres para buscar.
        </p>
      )}

      {loading && (
        <p
          className="text-sm text-slate-500"
          data-testid="explore-loading"
        >
          Buscando…
        </p>
      )}

      {error && (
        <p
          className="text-sm text-rose-600"
          data-testid="explore-error"
        >
          {error}
        </p>
      )}

      {showEmpty && (
        <p
          className="text-sm text-slate-500"
          data-testid="explore-empty"
        >
          No encontramos personas que coincidan con “{trimmed}”.
        </p>
      )}

      {response && response.data.length > 0 && (
        <section
          className="flex flex-col gap-3"
          data-testid="explore-results"
          aria-label="Resultados de búsqueda"
        >
          <p
            className="text-xs text-slate-500"
            data-testid="explore-results-count"
          >
            {response.meta.totalResults}{" "}
            {response.meta.totalResults === 1 ? "resultado" : "resultados"}
          </p>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {response.data.map((result) => (
              <UserSearchResultCard
                key={result.id}
                result={result}
                onFollowToggle={handleFollowToggle}
              />
            ))}
          </div>
        </section>
      )}
    </div>
  );
};