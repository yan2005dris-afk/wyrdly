import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Button, IconButton } from "./Button";

describe("Button & IconButton Components", () => {
  it("renders button label and handles click event", () => {
    const handleClick = vi.fn();
    render(<Button onClick={handleClick}>Publish</Button>);

    const button = screen.getByRole("button", { name: "Publish" });
    expect(button).toBeInTheDocument();
    fireEvent.click(button);
    expect(handleClick).toHaveBeenCalledTimes(1);
  });

  it("shows loading spinner and disables click when isLoading is true", () => {
    const handleClick = vi.fn();
    render(
      <Button isLoading={true} onClick={handleClick}>
        Publish
      </Button>,
    );

    const button = screen.getByRole("button");
    expect(button).toBeDisabled();
    expect(screen.getByTestId("button-spinner")).toBeInTheDocument();
    fireEvent.click(button);
    expect(handleClick).not.toHaveBeenCalled();
  });

  it("renders left and right icons", () => {
    render(
      <Button
        leftIcon={<span data-testid="left">←</span>}
        rightIcon={<span data-testid="right">→</span>}
      >
        Next
      </Button>,
    );

    expect(screen.getByTestId("button-left-icon")).toBeInTheDocument();
    expect(screen.getByTestId("button-right-icon")).toBeInTheDocument();
  });

  it("renders IconButton with aria-label and badge count", () => {
    render(
      <IconButton
        icon={<span>🔔</span>}
        ariaLabel="Notifications"
        badgeCount={9}
      />,
    );

    const button = screen.getByRole("button", { name: "Notifications" });
    expect(button).toBeInTheDocument();
    expect(screen.getByTestId("icon-button-badge")).toHaveTextContent("9");
  });

  it("renders 9+ when badge count exceeds 9", () => {
    render(
      <IconButton
        icon={<span>🔔</span>}
        ariaLabel="Notifications"
        badgeCount={15}
      />,
    );

    expect(screen.getByTestId("icon-button-badge")).toHaveTextContent("9+");
  });
});
