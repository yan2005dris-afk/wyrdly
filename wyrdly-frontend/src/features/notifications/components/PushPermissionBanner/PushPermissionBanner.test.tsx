import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { PushPermissionBanner } from "./PushPermissionBanner";

describe("PushPermissionBanner Component", () => {
  it("renders notice text and buttons", () => {
    render(<PushPermissionBanner onEnable={vi.fn()} onDismiss={vi.fn()} />);

    expect(
      screen.getByText(/Enable Desktop Push Notifications/),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Enable" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Not now" })).toBeInTheDocument();
  });

  it("handles Enable and Not Now clicks", () => {
    const handleEnable = vi.fn();
    const handleDismiss = vi.fn();

    render(
      <PushPermissionBanner
        onEnable={handleEnable}
        onDismiss={handleDismiss}
      />,
    );

    fireEvent.click(screen.getByTestId("enable-push-btn"));
    expect(handleEnable).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByTestId("dismiss-push-btn"));
    expect(handleDismiss).toHaveBeenCalledTimes(1);
  });
});
