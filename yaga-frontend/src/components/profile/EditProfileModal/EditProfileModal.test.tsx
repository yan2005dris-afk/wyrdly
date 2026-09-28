import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { EditProfileModal } from "./EditProfileModal";
import type { UserProfileSummary } from "../../../types/domain";

const mockSummary: UserProfileSummary = {
  id: "usr_123",
  username: "juanperez",
  fullName: "Juan Perez",
  bio: "Software Engineer",
  avatarUrl: "https://example.com/avatar.jpg",
  isVerified: false,
  instanceUrl: "relaymesh.io",
  stats: {
    postsCount: 5,
    followersCount: 10,
    followingCount: 20,
  },
};

describe("EditProfileModal Component", () => {
  it("renders form inputs with initial profile values", () => {
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
      screen.getByDisplayValue("https://example.com/avatar.jpg"),
    ).toBeInTheDocument();
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
    expect(handleClose).toHaveBeenCalled();
  });
});
