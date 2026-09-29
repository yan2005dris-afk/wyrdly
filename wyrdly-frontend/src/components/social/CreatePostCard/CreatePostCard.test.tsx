import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { CreatePostCard } from "./CreatePostCard";

describe("CreatePostCard Component", () => {
  it("renders textarea with placeholder", () => {
    render(<CreatePostCard onPublish={vi.fn()} />);

    expect(
      screen.getByPlaceholderText(
        "Share an update with your federated graph...",
      ),
    ).toBeInTheDocument();
  });

  it("disables publish button when textarea is empty", () => {
    render(<CreatePostCard onPublish={vi.fn()} />);

    const publishBtn = screen.getByRole("button", { name: /publish/i });
    expect(publishBtn).toBeDisabled();
  });

  it("enables publish button when text is entered and triggers onPublish", async () => {
    const handlePublish = vi.fn();
    render(<CreatePostCard onPublish={handlePublish} />);

    const textarea = screen.getByPlaceholderText(
      "Share an update with your federated graph...",
    );
    fireEvent.change(textarea, {
      target: { value: "Hello federated network!" },
    });

    const publishBtn = screen.getByRole("button", { name: /publish/i });
    expect(publishBtn).toBeEnabled();

    fireEvent.click(publishBtn);

    await waitFor(() => {
      expect(handlePublish).toHaveBeenCalledTimes(1);
      expect(handlePublish).toHaveBeenCalledWith({
        content: "Hello federated network!",
        visibility: "PUBLIC",
        attachments: undefined,
      });
    });
  });

  it("toggles visibility when clicked", () => {
    render(<CreatePostCard onPublish={vi.fn()} />);

    const toggleBtn = screen.getByTestId("visibility-toggle-btn");
    expect(toggleBtn).toHaveTextContent("public");

    fireEvent.click(toggleBtn);
    expect(toggleBtn).toHaveTextContent("federated");

    fireEvent.click(toggleBtn);
    expect(toggleBtn).toHaveTextContent("followers");
  });
});
