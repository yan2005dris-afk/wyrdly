import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { AuthGraphHero } from "./AuthGraphHero";

describe("AuthGraphHero Component", () => {
  it("renders brand, graph SVG, headline and feature cards", () => {
    render(<AuthGraphHero />);

    expect(screen.getByText("Wyrdly")).toBeInTheDocument();
    expect(
      screen.getByText("Where connections weave the future"),
    ).toBeInTheDocument();
    expect(screen.getByTestId("graph-svg")).toBeInTheDocument();
    expect(screen.getByText(/Your social graph,/)).toBeInTheDocument();
    expect(screen.getByText("Federated Relays")).toBeInTheDocument();
    expect(screen.getByText("Real-time Sync")).toBeInTheDocument();
    expect(screen.getByText("You Own Data")).toBeInTheDocument();
  });
});
