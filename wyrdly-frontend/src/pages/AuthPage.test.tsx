import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { BrowserRouter } from "react-router-dom";
import { AuthPage } from "./AuthPage";
import { AuthProvider } from "../features/auth";

const renderAuthPage = () => {
  return render(
    <AuthProvider>
      <BrowserRouter>
        <AuthPage />
      </BrowserRouter>
    </AuthProvider>,
  );
};

describe("AuthPage Component", () => {
  it("renders brand hero, graph SVG, and welcome message", () => {
    renderAuthPage();
    expect(screen.getByText("Wyrdly")).toBeInTheDocument();
    expect(
      screen.getByText("Where connections weave the future"),
    ).toBeInTheDocument();
    expect(screen.getByText(/Your social graph,/i)).toBeInTheDocument();
    expect(screen.getByText(/Welcome to Wyrdly/i)).toBeInTheDocument();
  });

  it("switches to Sign Up tab when Sign Up button is clicked", () => {
    renderAuthPage();
    const signUpTab = screen.getByRole("tab", { name: /^sign up$/i });
    fireEvent.click(signUpTab);

    expect(screen.getByText("Full Name")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("Your full name")).toBeInTheDocument();
    expect(screen.getByText("Email")).toBeInTheDocument();
    expect(screen.getByText("Short Bio")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /get started/i }),
    ).toBeInTheDocument();
  });

  it("switches back to Sign In tab", () => {
    renderAuthPage();
    const signUpTab = screen.getByRole("tab", { name: /^sign up$/i });
    fireEvent.click(signUpTab);
    expect(screen.getByText("Full Name")).toBeInTheDocument();

    const signInTab = screen.getByRole("tab", { name: /^sign in$/i });
    fireEvent.click(signInTab);
    expect(screen.queryByText("Full Name")).not.toBeInTheDocument();
    expect(screen.queryByText("Short Bio")).not.toBeInTheDocument();
  });

  it("toggles password visibility", () => {
    renderAuthPage();
    const passwordInput = screen.getByPlaceholderText(
      "••••••••",
    ) as HTMLInputElement;
    expect(passwordInput.type).toBe("password");

    const toggleButton = passwordInput.parentElement?.querySelector("button");
    expect(toggleButton).toBeTruthy();
    if (toggleButton) {
      fireEvent.click(toggleButton);
      expect(passwordInput.type).toBe("text");
      fireEvent.click(toggleButton);
      expect(passwordInput.type).toBe("password");
    }
  });
});
