import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import App from "./App";

describe("App Component", () => {
  it("renders AuthPage by default when unauthenticated", () => {
    render(<App />);
    expect(screen.getByText(/Welcome to Wyrdly/i)).toBeInTheDocument();
    expect(
      screen.getAllByRole("button", { name: /sign in/i }).length,
    ).toBeGreaterThanOrEqual(1);
    expect(screen.getByRole("tab", { name: /^sign up$/i })).toBeInTheDocument();
  });

  it("redirects an unauthenticated visitor on the root path to /login", () => {
    window.history.replaceState({}, "", "/");

    render(<App />);

    expect(window.location.pathname).toBe("/login");
    expect(screen.getByText(/Welcome to Wyrdly/i)).toBeInTheDocument();
    expect(
      screen.getAllByRole("button", { name: /sign in/i }).length,
    ).toBeGreaterThanOrEqual(1);
  });

  it("renders NotFoundPage when navigating to an unknown route", () => {
    window.history.replaceState({}, "", "/non-existent-route-404");

    render(<App />);

    expect(screen.getByTestId("not-found-page")).toBeInTheDocument();
    expect(screen.getByText("Page not found")).toBeInTheDocument();
  });
});
