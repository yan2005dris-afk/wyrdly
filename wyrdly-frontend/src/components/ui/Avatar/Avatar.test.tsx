import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Avatar } from "./Avatar";

describe("Avatar Component", () => {
  it("renders image with correct src and alt text", () => {
    render(
      <Avatar
        src="https://example.com/avatar.jpg"
        alt="Maya Krishnan"
        size="md"
      />,
    );

    const img = screen.getByRole("img");
    expect(img).toBeInTheDocument();
    expect(img).toHaveAttribute("src", "https://example.com/avatar.jpg");
    expect(img).toHaveAttribute("alt", "Maya Krishnan");
  });

  it("renders an inline-SVG fallback when src is not provided", () => {
    render(<Avatar alt="Maya Krishnan" />);
    const fallback = screen.getByTestId("avatar-fallback");
    expect(fallback).toBeInTheDocument();
    expect(fallback.tagName).toBe("IMG");
    expect(fallback.getAttribute("src")).toMatch(/^data:image\/svg\+xml/);
    // Sanity check: the SVG embeds the user's initials.
    const decoded = decodeURIComponent(fallback.getAttribute("src") ?? "");
    expect(decoded).toContain(">MK<");
  });

  it("renders the custom initials in the inline-SVG fallback", () => {
    render(<Avatar alt="Alice" fallbackInitials="AC" />);
    const fallback = screen.getByTestId("avatar-fallback");
    expect(fallback.tagName).toBe("IMG");
    const decoded = decodeURIComponent(fallback.getAttribute("src") ?? "");
    expect(decoded).toContain(">AC<");
  });

  it("renders online status badge when isOnline is true", () => {
    render(
      <Avatar
        src="https://example.com/avatar.jpg"
        alt="Maya Krishnan"
        isOnline={true}
      />,
    );
    expect(screen.getByTestId("avatar-online-dot")).toBeInTheDocument();
  });

  it("does not render online status badge when isOnline is false", () => {
    render(
      <Avatar
        src="https://example.com/avatar.jpg"
        alt="Maya Krishnan"
        isOnline={false}
      />,
    );
    expect(screen.queryByTestId("avatar-online-dot")).not.toBeInTheDocument();
  });

  it("switches from src to the inline-SVG fallback when the image errors", () => {
    render(<Avatar src="https://example.com/invalid.jpg" alt="Devon Park" />);
    const img = screen.getByRole("img");
    fireEvent.error(img);

    const fallback = screen.getByTestId("avatar-fallback");
    expect(fallback).toBeInTheDocument();
    const decoded = decodeURIComponent(fallback.getAttribute("src") ?? "");
    expect(decoded).toContain(">DP<");
  });

  it("calls onClick when clicked", () => {
    const handleClick = vi.fn();
    render(<Avatar alt="Maya" onClick={handleClick} />);
    const container = screen.getByTestId("avatar-container");
    fireEvent.click(container);
    expect(handleClick).toHaveBeenCalledTimes(1);
  });
});
