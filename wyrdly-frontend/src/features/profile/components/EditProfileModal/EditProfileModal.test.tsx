import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { EditProfileModal } from "./EditProfileModal";
import type { UserProfileSummary } from "../../../../types/domain";

const mockSummary: UserProfileSummary = {
  id: "usr_123",
  username: "juanperez",
  fullName: "Juan Perez",
  bio: "Software Engineer",
  avatarUrl: "https://example.com/avatar.jpg",
  isVerified: false,
  instanceUrl: "wyrdly.app",
  stats: {
    postsCount: 5,
    followersCount: 10,
    followingCount: 20,
  },
};

describe("EditProfileModal Component", () => {
  beforeEach(() => {
    vi.spyOn(URL, "createObjectURL").mockReturnValue(
      "blob:fake-avatar-preview",
    );
    vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("renders form inputs with initial profile values and avatar trigger", () => {
    render(
      <EditProfileModal
        isOpen={true}
        profile={mockSummary}
        isSaving={false}
        onSave={vi.fn()}
        onClose={vi.fn()}
      />,
    );

    expect(screen.getByDisplayValue("Juan Perez")).toBeInTheDocument();
    expect(screen.getByDisplayValue("Software Engineer")).toBeInTheDocument();
    expect(
      screen.getByTestId("edit-profile-avatar-trigger"),
    ).toBeInTheDocument();
    expect(screen.getByAltText("Juan Perez")).toHaveAttribute(
      "src",
      "https://example.com/avatar.jpg",
    );
  });

  it("calls onSave with updated form values when submitted", () => {
    const handleSave = vi.fn();
    render(
      <EditProfileModal
        isOpen={true}
        profile={mockSummary}
        isSaving={false}
        onSave={handleSave}
        onClose={vi.fn()}
      />,
    );

    const nameInput = screen.getByLabelText("Full Name");
    fireEvent.change(nameInput, { target: { value: "Juan Updated" } });

    const submitBtn = screen.getByTestId("edit-profile-save");
    fireEvent.click(submitBtn);

    expect(handleSave).toHaveBeenCalledWith({
      fullName: "Juan Updated",
      bio: "Software Engineer",
      avatarUrl: "https://example.com/avatar.jpg",
    });
  });

  it("triggers file input click when avatar trigger is clicked", () => {
    render(
      <EditProfileModal
        isOpen={true}
        profile={mockSummary}
        isSaving={false}
        onSave={vi.fn()}
        onClose={vi.fn()}
      />,
    );

    const fileInput = screen.getByTestId(
      "edit-profile-avatar-input",
    ) as HTMLInputElement;
    const clickSpy = vi.spyOn(fileInput, "click");

    const avatarTrigger = screen.getByTestId("edit-profile-avatar-trigger");
    fireEvent.click(avatarTrigger);

    expect(clickSpy).toHaveBeenCalled();
  });

  it("triggers file input on Enter and Space key presses for accessibility", () => {
    render(
      <EditProfileModal
        isOpen={true}
        profile={mockSummary}
        isSaving={false}
        onSave={vi.fn()}
        onClose={vi.fn()}
      />,
    );

    const fileInput = screen.getByTestId(
      "edit-profile-avatar-input",
    ) as HTMLInputElement;
    const clickSpy = vi.spyOn(fileInput, "click");
    const avatarTrigger = screen.getByTestId("edit-profile-avatar-trigger");

    fireEvent.keyDown(avatarTrigger, { key: "Enter" });
    expect(clickSpy).toHaveBeenCalledTimes(1);

    fireEvent.keyDown(avatarTrigger, { key: " " });
    expect(clickSpy).toHaveBeenCalledTimes(2);
  });

  it("uploads selected file, creates local preview, and passes uploaded URL to onSave", async () => {
    const handleSave = vi.fn();
    const uploadAvatar = vi
      .fn()
      .mockResolvedValue("https://storage.wyrdly.app/avatars/uploaded.webp");

    render(
      <EditProfileModal
        isOpen={true}
        profile={mockSummary}
        isSaving={false}
        onSave={handleSave}
        onClose={vi.fn()}
        uploadAvatar={uploadAvatar}
      />,
    );

    const fileInput = screen.getByTestId("edit-profile-avatar-input");
    const testFile = new File(["dummy content"], "avatar.png", {
      type: "image/png",
    });

    fireEvent.change(fileInput, { target: { files: [testFile] } });

    await waitFor(() => {
      expect(uploadAvatar).toHaveBeenCalledWith(testFile);
    });

    const submitBtn = screen.getByTestId("edit-profile-save");
    fireEvent.click(submitBtn);

    expect(handleSave).toHaveBeenCalledWith({
      fullName: "Juan Perez",
      bio: "Software Engineer",
      avatarUrl: "https://storage.wyrdly.app/avatars/uploaded.webp",
    });
  });

  it("displays error message when avatar upload fails", async () => {
    const uploadAvatar = vi
      .fn()
      .mockRejectedValue(new Error("File exceeds 10MB limit"));

    render(
      <EditProfileModal
        isOpen={true}
        profile={mockSummary}
        isSaving={false}
        onSave={vi.fn()}
        onClose={vi.fn()}
        uploadAvatar={uploadAvatar}
      />,
    );

    const fileInput = screen.getByTestId("edit-profile-avatar-input");
    const testFile = new File(["dummy"], "giant.png", { type: "image/png" });

    fireEvent.change(fileInput, { target: { files: [testFile] } });

    await waitFor(() => {
      expect(screen.getByTestId("edit-profile-avatar-error")).toHaveTextContent(
        "File exceeds 10MB limit",
      );
    });
  });

  it("disables submit button and shows loading text while upload is pending", async () => {
    let resolveUpload: (url: string | null) => void = () => {};
    const uploadPromise = new Promise<string | null>((resolve) => {
      resolveUpload = resolve;
    });
    const uploadAvatar = vi.fn().mockReturnValue(uploadPromise);

    render(
      <EditProfileModal
        isOpen={true}
        profile={mockSummary}
        isSaving={false}
        onSave={vi.fn()}
        onClose={vi.fn()}
        uploadAvatar={uploadAvatar}
      />,
    );

    const fileInput = screen.getByTestId("edit-profile-avatar-input");
    const testFile = new File(["dummy"], "photo.jpg", { type: "image/jpeg" });

    fireEvent.change(fileInput, { target: { files: [testFile] } });

    // While uploading
    expect(screen.getByTestId("edit-profile-save")).toBeDisabled();
    expect(screen.getByText("Uploading…")).toBeInTheDocument();

    // Resolve upload
    resolveUpload("https://storage.wyrdly.app/avatars/photo.webp");
    await waitFor(() => {
      expect(screen.queryByText("Uploading…")).not.toBeInTheDocument();
      expect(screen.getByTestId("edit-profile-save")).toBeEnabled();
    });
  });

  it("calls onClose when close button or cancel is clicked", () => {
    const handleClose = vi.fn();
    render(
      <EditProfileModal
        isOpen={true}
        profile={mockSummary}
        isSaving={false}
        onSave={vi.fn()}
        onClose={handleClose}
      />,
    );

    fireEvent.click(screen.getByTestId("edit-profile-close"));
    expect(handleClose).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByText("Cancel"));
    expect(handleClose).toHaveBeenCalledTimes(2);
  });
});
