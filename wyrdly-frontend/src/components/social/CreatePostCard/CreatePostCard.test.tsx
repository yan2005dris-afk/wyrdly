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
        mediaUrl: undefined,
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

  it("calls uploadMedia first, then onPublish with mediaUrl when a file is selected", async () => {
    const handlePublish = vi.fn();
    const uploadMedia = vi
      .fn()
      .mockResolvedValue("https://cdn.wyrdly.app/posts/img_abc.jpg");

    render(
      <CreatePostCard onPublish={handlePublish} uploadMedia={uploadMedia} />,
    );

    const file = new File(["binary"], "photo.jpg", { type: "image/jpeg" });
    const fileInput = screen.getByTestId("file-upload-input");
    fireEvent.change(fileInput, { target: { files: [file] } });

    const publishBtn = screen.getByRole("button", { name: /publish/i });
    fireEvent.click(publishBtn);

    await waitFor(() => {
      expect(uploadMedia).toHaveBeenCalledTimes(1);
    });
    expect(uploadMedia).toHaveBeenCalledWith(file);

    await waitFor(() => {
      expect(handlePublish).toHaveBeenCalledTimes(1);
    });
    expect(handlePublish).toHaveBeenCalledWith({
      content: "",
      visibility: "PUBLIC",
      attachments: [file],
      mediaUrl: "https://cdn.wyrdly.app/posts/img_abc.jpg",
    });
  });

  it("does not call onPublish when uploadMedia resolves with null", async () => {
    const handlePublish = vi.fn();
    const uploadMedia = vi.fn().mockResolvedValue(null);

    render(
      <CreatePostCard onPublish={handlePublish} uploadMedia={uploadMedia} />,
    );

    const file = new File(["binary"], "photo.jpg", { type: "image/jpeg" });
    const fileInput = screen.getByTestId("file-upload-input");
    fireEvent.change(fileInput, { target: { files: [file] } });

    const publishBtn = screen.getByRole("button", { name: /publish/i });
    fireEvent.click(publishBtn);

    await waitFor(() => {
      expect(uploadMedia).toHaveBeenCalledTimes(1);
    });

    // Give the rejected publish path a chance to fire (it must not).
    await new Promise((r) => setTimeout(r, 50));

    expect(handlePublish).not.toHaveBeenCalled();
    expect(screen.getByTestId("upload-error")).toBeInTheDocument();
  });

  it("disables publish button and shows Uploading indicator when isUploadingMedia is true", () => {
    render(
      <CreatePostCard
        onPublish={vi.fn()}
        uploadMedia={vi.fn()}
        isUploadingMedia={true}
      />,
    );

    const publishBtn = screen.getByTestId("publish-post-btn");
    expect(publishBtn).toBeDisabled();

    const indicator = screen.getByTestId("uploading-indicator");
    expect(indicator).toHaveTextContent(/uploading/i);
  });
});
