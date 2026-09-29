import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ThreeColumnLayout } from "./ThreeColumnLayout";

describe("ThreeColumnLayout Component", () => {
  it("renders navbar, banner, left sidebar, center main and right sidebar", () => {
    render(
      <ThreeColumnLayout
        navbar={<nav data-testid="mock-navbar">Navbar</nav>}
        banner={<div data-testid="mock-banner">Push Banner</div>}
        leftSidebar={<div data-testid="mock-left">Left Sidebar</div>}
        rightSidebar={<div data-testid="mock-right">Right Sidebar</div>}
      >
        <div data-testid="mock-content">Feed Content</div>
      </ThreeColumnLayout>,
    );

    expect(screen.getByTestId("mock-navbar")).toBeInTheDocument();
    expect(screen.getByTestId("mock-banner")).toBeInTheDocument();
    expect(screen.getByTestId("mock-left")).toBeInTheDocument();
    expect(screen.getByTestId("mock-content")).toBeInTheDocument();
    expect(screen.getByTestId("mock-right")).toBeInTheDocument();
  });

  it("renders without left or right sidebar gracefully", () => {
    render(
      <ThreeColumnLayout>
        <div data-testid="mock-solo-content">Solo Content</div>
      </ThreeColumnLayout>,
    );

    expect(screen.getByTestId("mock-solo-content")).toBeInTheDocument();
    expect(screen.queryByTestId("layout-left-sidebar")).not.toBeInTheDocument();
    expect(
      screen.queryByTestId("layout-right-sidebar"),
    ).not.toBeInTheDocument();
  });
});
