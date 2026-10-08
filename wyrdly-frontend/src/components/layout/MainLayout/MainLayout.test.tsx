import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { MainLayout } from "./MainLayout";
import { AuthProvider } from "../../../context/AuthContext";

vi.mock("../../../features/notifications/hooks/useNotifications", () => ({
  useNotifications: () => ({
    notifications: [],
    unreadCount: 0,
    isLoading: false,
    error: null,
    refetch: vi.fn(),
    markRead: vi.fn(),
    markAllRead: vi.fn(),
  }),
}));

describe("MainLayout Component", () => {
  it("renders navbar, persistent sidebar, and child outlet route content", () => {
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={["/"]}>
          <Routes>
            <Route element={<MainLayout />}>
              <Route
                path="/"
                element={<div data-testid="test-child">Child Content</div>}
              />
            </Route>
          </Routes>
        </MemoryRouter>
      </AuthProvider>,
    );

    expect(screen.getByTestId("main-layout")).toBeInTheDocument();
    expect(screen.getByTestId("app-navbar")).toBeInTheDocument();
    expect(screen.getByTestId("main-layout-sidebar")).toBeInTheDocument();
    expect(screen.getByTestId("user-summary-card")).toBeInTheDocument();
    expect(screen.getByTestId("view-profile-link")).toBeInTheDocument();
    expect(screen.queryByTestId("user-summary-stats")).not.toBeInTheDocument();
    expect(screen.getByTestId("sidebar-nav")).toBeInTheDocument();
    expect(screen.getByTestId("bottom-nav")).toBeInTheDocument();
    expect(screen.getByTestId("test-child")).toHaveTextContent("Child Content");
  });

  it("navigates to explore with query on navbar search submission", () => {
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={["/"]}>
          <Routes>
            <Route element={<MainLayout />}>
              <Route path="/" element={<div>Home</div>} />
              <Route
                path="/explore"
                element={
                  <div data-testid="explore-destination">Explore Page</div>
                }
              />
            </Route>
          </Routes>
        </MemoryRouter>
      </AuthProvider>,
    );

    const searchInput = screen.getByTestId("navbar-search-input");
    fireEvent.change(searchInput, { target: { value: "alice" } });
    fireEvent.submit(searchInput.closest("form")!);

    expect(screen.getByTestId("explore-destination")).toBeInTheDocument();
  });

  it("renders messages badge conditionally based on unreadMessagesCount", () => {
    const { rerender } = render(
      <AuthProvider>
        <MemoryRouter initialEntries={["/"]}>
          <Routes>
            <Route element={<MainLayout />}>
              <Route path="/" element={<div>Home</div>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </AuthProvider>,
    );

    expect(screen.queryByTestId("nav-badge-messages")).not.toBeInTheDocument();

    rerender(
      <AuthProvider>
        <MemoryRouter initialEntries={["/"]}>
          <Routes>
            <Route element={<MainLayout unreadMessagesCount={5} />}>
              <Route path="/" element={<div>Home</div>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </AuthProvider>,
    );

    expect(screen.getByTestId("nav-badge-messages")).toHaveTextContent("5");
  });
});
