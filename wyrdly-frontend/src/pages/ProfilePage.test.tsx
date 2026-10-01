import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { BrowserRouter } from "react-router-dom";
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

  it("renders profile header, RustFS cover notice and post grid items", async () => {
    renderProfilePage();

    await waitFor(() => {
      expect(screen.getByText("Back to feed")).toBeInTheDocument();
      expect(
        screen.getAllByText("Maya Krishnan").length,
      ).toBeGreaterThanOrEqual(1);
      expect(screen.getByText("Cover stored on RustFS")).toBeInTheDocument();
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
});
