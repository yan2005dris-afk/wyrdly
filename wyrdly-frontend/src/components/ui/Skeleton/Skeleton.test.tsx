import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Skeleton } from "./Skeleton";

describe("Skeleton Component", () => {
  it("renders with default props", () => {
    render(<Skeleton />);
    const el = screen.getByTestId("skeleton-element");
    expect(el).toBeInTheDocument();
    expect(el).toHaveAttribute("aria-hidden", "true");
  });

  it("applies width and height as inline styles", () => {
    render(<Skeleton width={120} height={32} />);
    const el = screen.getByTestId("skeleton-element");
    expect(el).toHaveStyle({ width: "120px", height: "32px" });
  });

  it("renders with custom className", () => {
    render(<Skeleton className="custom-skeleton" />);
    const el = screen.getByTestId("skeleton-element");
    expect(el.className).toContain("custom-skeleton");
  });

  it("renders circular variant", () => {
    render(<Skeleton variant="circular" width={40} height={40} />);
    const el = screen.getByTestId("skeleton-element");
    expect(el).toBeInTheDocument();
    expect(el).toHaveStyle({ width: "40px", height: "40px" });
  });
});
