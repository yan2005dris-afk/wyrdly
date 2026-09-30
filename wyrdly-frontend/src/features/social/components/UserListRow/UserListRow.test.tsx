import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { BrowserRouter } from "react-router-dom";
import { AuthProvider } from "../../../../features/auth";
import { UserListRow } from "./UserListRow";
import * as usersApi from "../../../../api/users";
import type { ProfileUserSummary } from "../../../../types/suggestions";

const ALLISON: ProfileUserSummary = {
  id: "usr_allison_04",
  username: "allison",
  fullName: "Allison Frontend",
  avatarUrl: "https://images.unsplash.com/allison.png",
  isFollowing: false,
};

const ANDY: ProfileUserSummary = {
  id: "usr_andy_03",
  username: "andy",
  fullName: "Andy Graph",
  avatarUrl: null,
  isFollowing: true,
};

function renderRow(user: ProfileUserSummary, subtitle?: string) {
  return render(
    <AuthProvider>
      <BrowserRouter>
        <UserListRow user={user} subtitle={subtitle} />
      </BrowserRouter>
    </AuthProvider>,
  );
}

describe("UserListRow", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    localStorage.setItem("wyrdly_token", "fake-jwt");

    vi.spyOn(usersApi.usersApi, "follow").mockResolvedValue({
      message: "ok",
      targetUserId: ALLISON.id,
      following: true,
    });
    vi.spyOn(usersApi.usersApi, "unfollow").mockResolvedValue({
      message: "ok",
      targetUserId: ANDY.id,
      following: false,
    });
  });

  it("renders avatar, name and @username", () => {
    renderRow(ALLISON);
    expect(screen.getByText("Allison Frontend")).toBeInTheDocument();
    expect(screen.getByText("@allison")).toBeInTheDocument();
    // Avatar fallback path: alt matches when image fails to load.
    const avatar = screen.getByAltText("Allison Frontend");
    expect(avatar).toBeInTheDocument();
  });

  it("renders subtitle only when provided", () => {
    const { rerender } = render(
      <AuthProvider>
        <BrowserRouter>
          <UserListRow user={ALLISON} />
        </BrowserRouter>
      </AuthProvider>,
    );
    expect(screen.queryByText("Mutual followers: 4")).not.toBeInTheDocument();

    rerender(
      <AuthProvider>
        <BrowserRouter>
          <UserListRow user={ALLISON} subtitle="Mutual followers: 4" />
        </BrowserRouter>
      </AuthProvider>,
    );
    expect(screen.getByText("Mutual followers: 4")).toBeInTheDocument();
  });

  it("renders 'Follow' for users you do not follow", () => {
    renderRow(ALLISON);
    const btn = screen.getByTestId(`follow-toggle-${ALLISON.id}`);
    expect(btn).toHaveTextContent("Follow");
    expect(btn).toHaveAttribute("data-following", "false");
  });

  it("renders 'Following' for users you already follow", () => {
    renderRow(ANDY);
    const btn = screen.getByTestId(`follow-toggle-${ANDY.id}`);
    expect(btn).toHaveTextContent("Following");
    expect(btn).toHaveAttribute("data-following", "true");
  });

  it("calls the follow API on click and flips optimistically", async () => {
    const followSpy = vi.spyOn(usersApi.usersApi, "follow");
    renderRow(ALLISON);

    const btn = screen.getByTestId(`follow-toggle-${ALLISON.id}`);
    expect(btn).toHaveTextContent("Follow");

    fireEvent.click(btn);

    // Optimistic: text flips immediately.
    await waitFor(() => {
      expect(btn).toHaveTextContent("Following");
    });

    await waitFor(() => {
      expect(followSpy).toHaveBeenCalledTimes(1);
    });
    expect(followSpy).toHaveBeenCalledWith(ALLISON.id);
  });

  it("calls the unfollow API on click when already following", async () => {
    const unfollowSpy = vi.spyOn(usersApi.usersApi, "unfollow");
    renderRow(ANDY);

    const btn = screen.getByTestId(`follow-toggle-${ANDY.id}`);
    expect(btn).toHaveTextContent("Following");

    fireEvent.click(btn);

    await waitFor(() => {
      expect(btn).toHaveTextContent("Follow");
    });

    await waitFor(() => {
      expect(unfollowSpy).toHaveBeenCalledTimes(1);
    });
    expect(unfollowSpy).toHaveBeenCalledWith(ANDY.id);
  });

  it("rolls back to the original state when the API rejects", async () => {
    vi.spyOn(usersApi.usersApi, "follow").mockRejectedValueOnce(
      new Error("network down"),
    );
    const consoleErrorSpy = vi
      .spyOn(console, "error")
      .mockImplementation(() => {});
    renderRow(ALLISON);

    const btn = screen.getByTestId(`follow-toggle-${ALLISON.id}`);
    expect(btn).toHaveTextContent("Follow");

    fireEvent.click(btn);

    // After the rejection the button must roll back to the original
    // 'Follow' label. The intermediate optimistic flip may or may not
    // be observable depending on React 19's automatic batching, so we
    // only assert the final state.
    await waitFor(() => {
      expect(btn).toHaveTextContent("Follow");
    });
    expect(consoleErrorSpy).toHaveBeenCalledWith(
      "Follow toggle failed",
      expect.any(Error),
    );

    consoleErrorSpy.mockRestore();
  });

  it("links to the user's profile", () => {
    renderRow(ALLISON);
    const link = screen.getByTestId(`user-list-row-${ALLISON.id}-link`);
    expect(link).toHaveAttribute("href", "/profile/allison");
  });

  it("calls onAfterToggle callback when toggle succeeds", async () => {
    const handleAfterToggle = vi.fn();
    render(
      <AuthProvider>
        <BrowserRouter>
          <UserListRow user={ALLISON} onAfterToggle={handleAfterToggle} />
        </BrowserRouter>
      </AuthProvider>,
    );

    const btn = screen.getByTestId(`follow-toggle-${ALLISON.id}`);
    fireEvent.click(btn);

    await waitFor(() => {
      expect(handleAfterToggle).toHaveBeenCalledTimes(1);
    });
    expect(handleAfterToggle).toHaveBeenCalledWith(ALLISON.id);
  });

  it("does not call onAfterToggle when the API rejects", async () => {
    vi.spyOn(usersApi.usersApi, "follow").mockRejectedValueOnce(
      new Error("network down"),
    );
    const consoleErrorSpy = vi
      .spyOn(console, "error")
      .mockImplementation(() => {});
    const handleAfterToggle = vi.fn();

    render(
      <AuthProvider>
        <BrowserRouter>
          <UserListRow user={ALLISON} onAfterToggle={handleAfterToggle} />
        </BrowserRouter>
      </AuthProvider>,
    );

    const btn = screen.getByTestId(`follow-toggle-${ALLISON.id}`);
    fireEvent.click(btn);

    await waitFor(() => {
      expect(btn).toHaveTextContent("Follow");
    });
    expect(handleAfterToggle).not.toHaveBeenCalled();

    consoleErrorSpy.mockRestore();
  });
});
