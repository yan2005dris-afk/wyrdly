import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { BrowserRouter } from "react-router-dom";
import { PostCard } from "./PostCard";
import type { Post } from "../../../types/feed";

const MOCK_POST: Post = {
  id: "post-1",
  author: {
    id: "user-1",
    username: "jonas",
    fullName: "Jonas Weber",
    avatarUrl: "https://example.com/jonas.jpg",
    isVerified: true,
    instanceUrl: "mastodon.social",
    stats: { followersCount: 1200, followingCount: 300, postsCount: 450 },
  },
  content:
    "Just shipped our relay cluster to 99.99% uptime. Decentralized social feels instant.",
  createdAt: "12m",
  attachments: [
    {
      id: "att-1",
      url: "https://example.com/cluster.jpg",
      storageProvider: "RUSTFS_S3",
      mimeType: "image/jpeg",
    },
  ],
  reactions: {
    LIKE: 1240,
    LOVE: 50,
    CELEBRATE: 20,
    RETWEET: 342,
  },
  userReaction: "LIKE",
  commentsCount: 89,
  visibility: "PUBLIC",
};

describe("PostCard Component", () => {
  it("renders author name, verification badge and text content", () => {
    render(
      <BrowserRouter>
        <PostCard post={MOCK_POST} />
      </BrowserRouter>,
    );

    expect(screen.getByText("Jonas Weber")).toBeInTheDocument();
    expect(screen.getByTestId("post-author-verified")).toBeInTheDocument();
    expect(
      screen.getByText(/Just shipped our relay cluster/),
    ).toBeInTheDocument();
  });

  it("renders RustFS S3 Bucket media badge", () => {
    render(
      <BrowserRouter>
        <PostCard post={MOCK_POST} />
      </BrowserRouter>,
    );

    expect(screen.getByTestId("rustfs-badge")).toHaveTextContent(
      "RustFS S3 Bucket",
    );
  });

  it("triggers onReaction when like or retweet is clicked", () => {
    const handleReaction = vi.fn();
    render(
      <BrowserRouter>
        <PostCard post={MOCK_POST} onReaction={handleReaction} />
      </BrowserRouter>,
    );

    const likeBtn = screen.getByTestId("like-btn");
    fireEvent.click(likeBtn);
    expect(handleReaction).toHaveBeenCalledWith("post-1", "LIKE");

    const boostBtn = screen.getByTestId("boost-btn");
    fireEvent.click(boostBtn);
    expect(handleReaction).toHaveBeenCalledWith("post-1", "RETWEET");
  });
});
