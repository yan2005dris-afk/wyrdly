import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { pushCopy } from "../../copy";
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

  it("shows insecure-context copy when reason='insecure-context'", () => {
    render(
      <PushPermissionBanner
        onEnable={vi.fn()}
        onDismiss={vi.fn()}
        reason="insecure-context"
      />,
    );
    expect(screen.getByText(/HTTPS o localhost/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Got it" })).toBeInTheDocument();
    expect(screen.getByTestId("push-permission-banner").dataset.reason).toBe(
      "insecure-context",
    );
  });

  it("shows denied copy when reason='denied'", () => {
    render(
      <PushPermissionBanner
        onEnable={vi.fn()}
        onDismiss={vi.fn()}
        reason="denied"
      />,
    );
    expect(
      screen.getByText(/Notifications blocked in this browser/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(pushCopy.denied, { exact: false }),
    ).toBeInTheDocument();
  });

  it("shows unsupported copy when reason='unsupported'", () => {
    render(
      <PushPermissionBanner
        onEnable={vi.fn()}
        onDismiss={vi.fn()}
        reason="unsupported"
      />,
    );
    expect(
      screen.getByText(/Push notifications unavailable/),
    ).toBeInTheDocument();
  });

  it("shows missing-cache copy when reason='missing-cache'", () => {
    render(
      <PushPermissionBanner
        onEnable={vi.fn()}
        onDismiss={vi.fn()}
        reason="missing-cache"
      />,
    );
    expect(screen.getByText(/Cache API required/)).toBeInTheDocument();
  });
});
