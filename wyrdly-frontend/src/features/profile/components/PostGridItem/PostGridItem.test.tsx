import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { PostGridItem } from "./PostGridItem";

describe("PostGridItem Component", () => {
  it("renders post title, image and formatted stats", () => {
    render(
      <PostGridItem
        id="item-1"
        imageUrl="https://example.com/photo.jpg"
        title="Night deploys hit different"
        likesCount={412}
        repliesCount={38}
      />,
    );

    expect(screen.getByText("Night deploys hit different")).toBeInTheDocument();
    expect(screen.getByText("412 likes • 38 replies")).toBeInTheDocument();
    const img = screen.getByRole("img");
    expect(img).toHaveAttribute("src", "https://example.com/photo.jpg");
  });

  it("fires onClick handler when card is clicked", () => {
    const handleClick = vi.fn();
    render(
      <PostGridItem
        id="item-1"
        imageUrl="https://example.com/photo.jpg"
        title="Night deploys"
        likesCount={10}
        repliesCount={2}
        onClick={handleClick}
      />,
    );

    const item = screen.getByTestId("post-grid-item-item-1");
    fireEvent.click(item);
    expect(handleClick).toHaveBeenCalledTimes(1);
    expect(handleClick).toHaveBeenCalledWith("item-1");
  });
});
