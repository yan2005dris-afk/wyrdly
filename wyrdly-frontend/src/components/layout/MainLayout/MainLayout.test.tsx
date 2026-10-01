import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import { MainLayout } from "./MainLayout";
import { AuthProvider } from "../../../context/AuthContext";

describe("MainLayout Component", () => {
  it("renders navbar, persistent sidebar, and child outlet route content", () => {
    render(
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            <Route element={<MainLayout />}>
              <Route
                path="/"
                element={<div data-testid="test-child">Child Content</div>}
              />
            </Route>
          </Routes>
        </BrowserRouter>
      </AuthProvider>,
    );

    expect(screen.getByTestId("main-layout")).toBeInTheDocument();
    expect(screen.getByTestId("app-navbar")).toBeInTheDocument();
    expect(screen.getByTestId("main-layout-sidebar")).toBeInTheDocument();
    expect(screen.getByTestId("sidebar-nav")).toBeInTheDocument();
    expect(screen.getByTestId("test-child")).toHaveTextContent("Child Content");
  });
});
