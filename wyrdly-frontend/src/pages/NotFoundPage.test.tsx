import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { BrowserRouter, MemoryRouter } from "react-router-dom";
import { NotFoundPage } from "./NotFoundPage";
import { useAuth } from "../features/auth";

vi.mock("../features/auth", () => ({
  useAuth: vi.fn(),
}));

const mockNavigate = vi.fn();
vi.mock("react-router-dom", async () => {
  const actual =
    await vi.importActual<typeof import("react-router-dom")>(
      "react-router-dom",
    );
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

describe("NotFoundPage Component", () => {
  it("renders 404 heading and back to feed button for authenticated users", () => {
    vi.mocked(useAuth).mockReturnValue({
      isAuthenticated: true,
      user: {
        id: "usr-1",
        username: "alice",
        fullName: "Alice",
        email: "alice@example.com",
      },
      token: "fake-jwt",
      isLoading: false,
      login: vi.fn(),
      register: vi.fn(),
      logout: vi.fn(),
      updateUser: vi.fn(),
    });

    render(
      <MemoryRouter>
        <NotFoundPage />
      </MemoryRouter>,
    );

    expect(screen.getByTestId("not-found-page")).toBeInTheDocument();
    expect(screen.getByText("404")).toBeInTheDocument();
    expect(screen.getByText("Page not found")).toBeInTheDocument();
    expect(screen.getByTestId("not-found-home-btn")).toHaveTextContent(
      "Back to Feed",
    );
  });

  it("renders sign in button for unauthenticated users", () => {
    vi.mocked(useAuth).mockReturnValue({
      isAuthenticated: false,
      user: null,
      token: null,
      isLoading: false,
      login: vi.fn(),
      register: vi.fn(),
      logout: vi.fn(),
      updateUser: vi.fn(),
    });

    render(
      <MemoryRouter>
        <NotFoundPage />
      </MemoryRouter>,
    );

    expect(screen.getByTestId("not-found-home-btn")).toHaveTextContent(
      "Sign In to Wyrdly",
    );
  });

  it("triggers navigate(-1) when Go Back button is clicked", () => {
    vi.mocked(useAuth).mockReturnValue({
      isAuthenticated: true,
      user: null,
      token: null,
      isLoading: false,
      login: vi.fn(),
      register: vi.fn(),
      logout: vi.fn(),
      updateUser: vi.fn(),
    });

    render(
      <BrowserRouter>
        <NotFoundPage />
      </BrowserRouter>,
    );

    const backBtn = screen.getByTestId("not-found-back-btn");
    fireEvent.click(backBtn);

    expect(mockNavigate).toHaveBeenCalledWith(-1);
  });
});
