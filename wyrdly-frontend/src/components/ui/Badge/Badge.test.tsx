import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Badge } from "./Badge";

describe("Badge Component", () => {
  it("renders children text correctly", () => {
    render(<Badge>Federated • Live</Badge>);
    expect(screen.getByText("Federated • Live")).toBeInTheDocument();
  });

  it("renders ping dot animation when ping is true", () => {
    render(
      <Badge variant="federated" ping={true}>
        Live
      </Badge>,
    );
    expect(screen.getByTestId("badge-ping")).toBeInTheDocument();
  });

  it("does not render ping dot when ping is false", () => {
    render(
      <Badge variant="federated" ping={false}>
        Live
      </Badge>,
    );
    expect(screen.queryByTestId("badge-ping")).not.toBeInTheDocument();
  });

  it("renders custom icon slot when provided", () => {
    render(
      <Badge icon={<span data-testid="custom-icon">★</span>}>Featured</Badge>,
    );
    expect(screen.getByTestId("custom-icon")).toBeInTheDocument();
  });

  it("renders counter variant correctly", () => {
    render(<Badge variant="counter">9+</Badge>);
    const badge = screen.getByTestId("badge-element");
    expect(badge).toHaveTextContent("9+");
  });
});
