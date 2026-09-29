import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Input } from "./Input";

describe("Input Component", () => {
  it("renders with label and placeholder", () => {
    render(
      <Input
        label="Username"
        placeholder="Enter username"
        value="maya"
        onChange={vi.fn()}
      />,
    );

    expect(screen.getByLabelText("Username")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("Enter username")).toBeInTheDocument();
    expect(screen.getByDisplayValue("maya")).toBeInTheDocument();
  });

  it("handles user typing and triggers onChange", () => {
    const handleChange = vi.fn();
    render(<Input placeholder="Search..." onChange={handleChange} />);

    const input = screen.getByPlaceholderText("Search...");
    fireEvent.change(input, { target: { value: "relay" } });

    expect(handleChange).toHaveBeenCalledTimes(1);
  });

  it("renders left icon and right element", () => {
    render(
      <Input
        leftIcon={<span data-testid="prefix-at">@</span>}
        rightElement={<button type="button">Show</button>}
      />,
    );

    expect(screen.getByTestId("input-left-icon")).toBeInTheDocument();
    expect(screen.getByTestId("input-right-element")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Show" })).toBeInTheDocument();
  });

  it("displays error message and sets aria-invalid='true'", () => {
    render(<Input label="Email" errorText="Invalid email address" />);

    const input = screen.getByLabelText("Email");
    expect(input).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByTestId("input-error-text")).toHaveTextContent(
      "Invalid email address",
    );
  });
});
