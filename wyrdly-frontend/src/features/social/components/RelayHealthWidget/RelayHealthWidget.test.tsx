import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { RelayHealthWidget } from "./RelayHealthWidget";

describe("RelayHealthWidget Component", () => {
  it("renders health status and federated instances description", () => {
    render(
      <RelayHealthWidget
        health="EXCELLENT"
        federatedInstancesCount={14}
        regionsSummary="EU & APAC"
      />,
    );

    expect(screen.getByText("Relay health: Excellent")).toBeInTheDocument();
    expect(
      screen.getByText(
        "You are federating with 14 instances across EU & APAC.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByTestId("relay-health-icon")).toBeInTheDocument();
  });

  it("renders correct number of latency segments", () => {
    render(
      <RelayHealthWidget latencySegments={["excellent", "good", "idle"]} />,
    );

    expect(screen.getByTestId("latency-segment-0")).toBeInTheDocument();
    expect(screen.getByTestId("latency-segment-1")).toBeInTheDocument();
    expect(screen.getByTestId("latency-segment-2")).toBeInTheDocument();
    expect(screen.queryByTestId("latency-segment-3")).not.toBeInTheDocument();
  });
});
