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

  it("renders fallback initials when src is not provided", () => {
    render(<Avatar alt="Maya Krishnan" />);
    const fallback = screen.getByTestId("avatar-fallback");
    expect(fallback).toBeInTheDocument();
    expect(fallback).toHaveTextContent("MK");
  });

  it("renders custom fallback initials when provided", () => {
    render(<Avatar alt="Alice" fallbackInitials="AC" />);
    const fallback = screen.getByTestId("avatar-fallback");
    expect(fallback).toHaveTextContent("AC");
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

  it("handles image error and falls back to initials", () => {
    render(<Avatar src="https://example.com/invalid.jpg" alt="Devon Park" />);
    const img = screen.getByRole("img");
    fireEvent.error(img);

    expect(screen.getByTestId("avatar-fallback")).toBeInTheDocument();
    expect(screen.getByTestId("avatar-fallback")).toHaveTextContent("DP");
  });

  it("calls onClick when clicked", () => {
    const handleClick = vi.fn();
    render(<Avatar alt="Maya" onClick={handleClick} />);
    const container = screen.getByTestId("avatar-container");
    fireEvent.click(container);
    expect(handleClick).toHaveBeenCalledTimes(1);
  });
});
