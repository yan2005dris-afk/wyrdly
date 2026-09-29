import { render, screen, fireEvent, waitFor, act } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { ExplorePage } from "./ExplorePage";
import { userSearchApi } from "../api/userSearch";
import type { UserSearchResponse } from "../types/userSearch";

vi.mock("../api/userSearch", () => ({
  userSearchApi: {
    searchUsers: vi.fn(),
  },
}));

const mockSearchUsers = vi.mocked(userSearchApi.searchUsers);

const renderWithRoute = (initialUrl: string) =>
  render(
    <MemoryRouter initialEntries={[initialUrl]}>
      <Routes>
        <Route path="/explore" element={<ExplorePage />} />
        <Route path="/profile/:username" element={<div>profile-page</div>} />
      </Routes>
    </MemoryRouter>,
  );

const successResponse: UserSearchResponse = {
  data: [
    {
      id: "usr_alice",
      username: "alice",
      fullName: "Alice Chen",
      avatarUrl: null,
      bio: "Backend dev",
      isFollowing: false,
      mutualConnectionSnippet: null,
    },
    {
      id: "usr_alicia",
      username: "alicia",
      fullName: "Alicia Keys",
      avatarUrl: null,
      bio: null,
      isFollowing: true,
      mutualConnectionSnippet: "2 amigos en común",
    },
  ],
  meta: { page: 0, pageSize: 20, totalResults: 2 },
};

describe("ExplorePage", () => {
  beforeEach(() => {
    mockSearchUsers.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("renders title and search input", () => {
    renderWithRoute("/explore");
    expect(screen.getByTestId("explore-title")).toHaveTextContent("Explorar");
    expect(screen.getByTestId("explore-search-input")).toBeInTheDocument();
  });

  it("does not call the API when q is shorter than 2 chars", async () => {
    renderWithRoute("/explore?q=a");
    await waitFor(() => {
      expect(mockSearchUsers).not.toHaveBeenCalled();
    });
    expect(screen.getByTestId("explore-min-hint")).toBeInTheDocument();
  });

  it("does not call the API when q is empty", async () => {
    renderWithRoute("/explore");
    await waitFor(() => {
      expect(mockSearchUsers).not.toHaveBeenCalled();
    });
    expect(screen.queryByTestId("explore-results")).not.toBeInTheDocument();
  });

  it("calls the API with the trimmed query from URL", async () => {
    mockSearchUsers.mockResolvedValueOnce(successResponse);
    renderWithRoute("/explore?q=alice");
    await waitFor(() => {
      expect(mockSearchUsers).toHaveBeenCalledWith({ q: "alice" });
    });
  });

  it("renders result cards after a successful search", async () => {
    mockSearchUsers.mockResolvedValueOnce(successResponse);
    renderWithRoute("/explore?q=alice");
    await waitFor(() => {
      expect(screen.getByTestId("user-search-result-usr_alice")).toBeInTheDocument();
    });
    expect(screen.getByTestId("user-search-result-usr_alicia")).toBeInTheDocument();
    expect(screen.getByTestId("explore-results-count")).toHaveTextContent("2 resultados");
  });

  it("renders empty state when there are no results", async () => {
    mockSearchUsers.mockResolvedValueOnce({
      data: [],
      meta: { page: 0, pageSize: 20, totalResults: 0 },
    });
    renderWithRoute("/explore?q=zzzzz");
    await waitFor(() => {
      expect(screen.getByTestId("explore-empty")).toBeInTheDocument();
    });
  });

  it("renders singular count when there is exactly one result", async () => {
    mockSearchUsers.mockResolvedValueOnce({
      data: [successResponse.data[0]],
      meta: { page: 0, pageSize: 20, totalResults: 1 },
    });
    renderWithRoute("/explore?q=alice");
    await waitFor(() => {
      expect(screen.getByTestId("explore-results-count")).toHaveTextContent("1 resultado");
    });
  });

  it("renders error message when the API throws", async () => {
    mockSearchUsers.mockRejectedValueOnce({
      response: { data: { message: "El backend explotó" } },
    });
    renderWithRoute("/explore?q=alice");
    await waitFor(() => {
      expect(screen.getByTestId("explore-error")).toHaveTextContent("El backend explotó");
    });
  });

  it("renders a fallback error message when no backend message is present", async () => {
    mockSearchUsers.mockRejectedValueOnce(new Error("network"));
    renderWithRoute("/explore?q=alice");
    await waitFor(() => {
      expect(screen.getByTestId("explore-error")).toHaveTextContent(
        "No se pudo completar la búsqueda.",
      );
    });
  });

  it("navigates to the new query when form is submitted", async () => {
    mockSearchUsers.mockResolvedValueOnce(successResponse);
    renderWithRoute("/explore?q=alice");
    await waitFor(() => {
      expect(mockSearchUsers).toHaveBeenCalled();
    });
    mockSearchUsers.mockClear();
    mockSearchUsers.mockResolvedValueOnce(successResponse);

    const input = screen.getByTestId("explore-search-input");
    await act(async () => {
      fireEvent.change(input, { target: { value: "bob" } });
      fireEvent.submit(input.closest("form")!);
    });

    await waitFor(() => {
      expect(mockSearchUsers).toHaveBeenCalledWith({ q: "bob" });
    });
  });

  it("toggles isFollowing optimistically when the follow button is clicked", async () => {
    mockSearchUsers.mockResolvedValueOnce(successResponse);
    renderWithRoute("/explore?q=alice");
    await waitFor(() => {
      expect(screen.getByTestId("user-search-follow-btn-usr_alice")).toHaveTextContent("Follow");
    });

    await act(async () => {
      fireEvent.click(screen.getByTestId("user-search-follow-btn-usr_alice"));
    });

    expect(screen.getByTestId("user-search-follow-btn-usr_alice")).toHaveTextContent("Following");
  });

  it("debounces API calls when only the draft changes", async () => {
    vi.useFakeTimers();
    mockSearchUsers.mockResolvedValue(successResponse);
    renderWithRoute("/explore");
    const input = screen.getByTestId("explore-search-input");

    await act(async () => {
      fireEvent.change(input, { target: { value: "a" } });
      fireEvent.change(input, { target: { value: "al" } });
    });
    expect(mockSearchUsers).not.toHaveBeenCalled();

    await act(async () => {
      fireEvent.submit(input.closest("form")!);
    });

    await act(async () => {
      vi.advanceTimersByTime(500);
    });

    expect(mockSearchUsers).toHaveBeenCalledTimes(1);
    expect(mockSearchUsers).toHaveBeenCalledWith({ q: "al" });
  });
});