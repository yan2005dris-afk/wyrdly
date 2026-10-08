import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { BrowserRouter, MemoryRouter, Routes, Route } from "react-router-dom";
import { ProfilePage } from "./ProfilePage";
import { AuthProvider } from "../context/AuthContext";
import { usersApi } from "../api/users";

vi.mock("../api/users", () => ({
  usersApi: {
    getProfile: vi.fn(),
    updateProfile: vi.fn(),
    getUserPosts: vi.fn(),
    getUserFollowers: vi.fn(),
    getUserFollowing: vi.fn(),
  },
}));

const mockProfile = {
  id: "user-maya",
  username: "maya",
  fullName: "Maya Krishnan",
  bio: "Senior Distributed Systems Engineer. Exploring libp2p, Neo4j graph traversal and rustfs storage.",
  avatarUrl:
    "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200&auto=format&fit=crop&q=80",
  followersCount: 1420,
  followingCount: 380,
  postsCount: 24,
  isFollowing: false,
  createdAt: "2024-08-15T10:00:00Z",
};

const renderProfilePage = () => {
  return render(
    <AuthProvider>
      <BrowserRouter>
        <ProfilePage />
      </BrowserRouter>
    </AuthProvider>,
  );
};

const samplePost = {
  id: "post-maya-1",
  content: "Night deploys hit different...",
  mediaUrl: "https://example.com/night-deploy.jpg",
  createdAt: "2026-01-10T12:00:00Z",
  author: {
    id: "user-maya",
    username: "maya",
    fullName: "Maya Krishnan",
    avatarUrl: mockProfile.avatarUrl,
  },
};

describe("ProfilePage Component", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.setItem(
      "wyrdly_user",
      JSON.stringify({
        id: "user-maya",
        username: "maya",
        fullName: "Maya Krishnan",
        email: "maya@wyrdly.social",
      }),
    );
    localStorage.setItem("wyrdly_token", "fake-token");
    vi.mocked(usersApi.getProfile).mockResolvedValue(mockProfile);
    vi.mocked(usersApi.getUserPosts).mockResolvedValue([samplePost]);
    vi.mocked(usersApi.getUserFollowers).mockResolvedValue([]);
    vi.mocked(usersApi.getUserFollowing).mockResolvedValue([]);
  });

  it("renders profile header and post grid items without debug badges", async () => {
    renderProfilePage();

    await waitFor(() => {
      expect(screen.getByText("Back to feed")).toBeInTheDocument();
      expect(
        screen.getAllByText("Maya Krishnan").length,
      ).toBeGreaterThanOrEqual(1);
      expect(
        screen.queryByTestId("cover-rustfs-badge"),
      ).not.toBeInTheDocument();
      expect(screen.getByTestId("profile-posts-grid")).toBeInTheDocument();
      expect(
        screen.getByText("Night deploys hit different..."),
      ).toBeInTheDocument();
    });
  });

  it("shows error state when profile API fails", async () => {
    vi.mocked(usersApi.getProfile).mockRejectedValueOnce(
      new Error("User not found"),
    );
    renderProfilePage();

    await waitFor(() => {
      expect(screen.getByText("User not found")).toBeInTheDocument();
      expect(screen.getByText("Try again")).toBeInTheDocument();
    });
  });

  it("renders Message button for other profiles and handles click", async () => {
    vi.mocked(usersApi.getProfile).mockResolvedValueOnce({
      ...mockProfile,
      id: "usr_alice",
      username: "alice",
      fullName: "Alice Chen",
    });
    renderProfilePage();

    await waitFor(() => {
      expect(screen.getByTestId("profile-message-btn")).toBeInTheDocument();
    });

    const msgBtn = screen.getByTestId("profile-message-btn");
    fireEvent.click(msgBtn);
  });

  it("renders ProfileHeaderSkeleton while profile is loading", async () => {
    vi.mocked(usersApi.getProfile).mockReturnValue(new Promise(() => {}));
    renderProfilePage();

    expect(screen.getByTestId("profile-loading")).toBeInTheDocument();
    expect(screen.getByTestId("profile-header-skeleton")).toBeInTheDocument();
    expect(screen.getAllByTestId("post-grid-item-skeleton")).toHaveLength(3);
  });

  it("renders post grid skeletons while user posts are loading", async () => {
    vi.mocked(usersApi.getUserPosts).mockReturnValue(new Promise(() => {}));
    renderProfilePage();

    await waitFor(() => {
      expect(screen.getByTestId("profile-posts-loading")).toBeInTheDocument();
    });
    expect(screen.getAllByTestId("post-grid-item-skeleton")).toHaveLength(6);
  });

  it("renders user row skeletons while followers are loading", async () => {
    vi.mocked(usersApi.getUserFollowers).mockReturnValue(new Promise(() => {}));
    renderProfilePage();

    await waitFor(() => {
      expect(
        screen.getByRole("tab", { name: "Followers" }),
      ).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("tab", { name: "Followers" }));

    await waitFor(() => {
      expect(
        screen.getByTestId("profile-followers-list-loading"),
      ).toBeInTheDocument();
    });
    expect(screen.getAllByTestId("user-list-row-skeleton")).toHaveLength(4);
  });

  it("fetches and renders profile when navigated with canonical user id", async () => {
    vi.mocked(usersApi.getProfile).mockResolvedValue({
      ...mockProfile,
      id: "usr_maya_id",
    });
    vi.mocked(usersApi.getUserPosts).mockResolvedValue([samplePost]);

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={["/profile/usr_maya_id"]}>
          <Routes>
            <Route path="/profile/:username" element={<ProfilePage />} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>,
    );

    await waitFor(() => {
      expect(usersApi.getProfile).toHaveBeenCalledWith("usr_maya_id");
    });
  });
});
