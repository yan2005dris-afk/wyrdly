import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { AuthFormCard } from "./AuthFormCard";

describe("AuthFormCard Component", () => {
  it("switches to Sign Up tab and renders extra fields", () => {
    render(<AuthFormCard onSignIn={vi.fn()} onSignUp={vi.fn()} />);

    const signUpTab = screen.getByTestId("tab-signup");
    fireEvent.click(signUpTab);

    expect(screen.getByTestId("input-fullname")).toBeInTheDocument();
    expect(screen.getByTestId("input-bio")).toBeInTheDocument();
    expect(screen.getByText("Get Started")).toBeInTheDocument();
  });

  it("toggles password visibility", () => {
    render(<AuthFormCard onSignIn={vi.fn()} onSignUp={vi.fn()} />);

    const passwordInput = screen.getByTestId(
      "input-password",
    ) as HTMLInputElement;
    expect(passwordInput.type).toBe("password");

    const toggleBtn = screen.getByTestId("toggle-password-btn");
    fireEvent.click(toggleBtn);
    expect(passwordInput.type).toBe("text");

    fireEvent.click(toggleBtn);
    expect(passwordInput.type).toBe("password");
  });

  it("triggers onSignIn when submitting sign in form", () => {
    const handleSignIn = vi.fn();
    render(<AuthFormCard onSignIn={handleSignIn} onSignUp={vi.fn()} />);

    const usernameInput = screen.getByTestId("input-username");
    const passwordInput = screen.getByTestId("input-password");
    fireEvent.change(usernameInput, { target: { value: "testuser" } });
    fireEvent.change(passwordInput, {
      target: { value: "mock-test-input-pass" },
    });

    const submitBtn = screen.getByTestId("auth-submit-btn");
    fireEvent.click(submitBtn);

    expect(handleSignIn).toHaveBeenCalledTimes(1);
    expect(handleSignIn).toHaveBeenCalledWith({
      usernameOrEmail: "testuser",
      password: "mock-test-input-pass",
    });
  });

  it("triggers onSignUp when submitting sign up form", () => {
    const handleSignUp = vi.fn();
    render(<AuthFormCard onSignIn={vi.fn()} onSignUp={handleSignUp} />);

    const signUpTab = screen.getByTestId("tab-signup");
    fireEvent.click(signUpTab);

    fireEvent.change(screen.getByTestId("input-fullname"), {
      target: { value: "Test User" },
    });
    fireEvent.change(screen.getByTestId("input-username"), {
      target: { value: "testuser" },
    });
    fireEvent.change(screen.getByTestId("input-email"), {
      target: { value: "testuser@example.com" },
    });
    fireEvent.change(screen.getByTestId("input-password"), {
      target: { value: "mock-test-input-pass" },
    });

    const submitBtn = screen.getByTestId("auth-submit-btn");
    fireEvent.click(submitBtn);

    expect(handleSignUp).toHaveBeenCalledTimes(1);
    expect(handleSignUp).toHaveBeenCalledWith({
      fullName: "Test User",
      username: "testuser",
      email: "testuser@example.com",
      password: "mock-test-input-pass",
      bio: undefined,
    });
  });
});
