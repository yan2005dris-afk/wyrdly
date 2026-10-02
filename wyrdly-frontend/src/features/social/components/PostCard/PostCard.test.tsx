import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { BrowserRouter } from "react-router-dom";
import { PostCard } from "./PostCard";
import type { Post } from "../../../../types/feed";

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

const renderCard = (props: Partial<React.ComponentProps<typeof PostCard>> = {}) =>
  render(
    <BrowserRouter>
      <PostCard post={MOCK_POST} {...props} />
    </BrowserRouter>,
  );

describe("PostCard Component", () => {
  it("renders author name, verification badge and text content", () => {
    renderCard();

    expect(screen.getByText("Jonas Weber")).toBeInTheDocument();
    expect(screen.getByTestId("post-author-verified")).toBeInTheDocument();
    expect(
      screen.getByText(/Just shipped our relay cluster/),
    ).toBeInTheDocument();
  });

  it("renders RustFS S3 Bucket media badge", () => {
    renderCard();

    expect(screen.getByTestId("rustfs-badge")).toHaveTextContent(
      "RustFS S3 Bucket",
    );
  });

  it("triggers onReaction when like or boost is clicked", () => {
    const handleReaction = vi.fn();
    renderCard({ onReaction: handleReaction });

    const likeBtn = screen.getByTestId("like-btn");
    fireEvent.click(likeBtn);
    expect(handleReaction).toHaveBeenCalledWith("post-1", "LIKE");

    const boostBtn = screen.getByTestId("boost-btn");
    fireEvent.click(boostBtn);
    expect(handleReaction).toHaveBeenCalledWith("post-1", "RETWEET");
  });

  it("renders three reaction buttons (LIKE, LOVE, CELEBRATE)", () => {
    renderCard();

    expect(screen.getByTestId("like-btn")).toBeInTheDocument();
    expect(screen.getByTestId("love-btn")).toBeInTheDocument();
    expect(screen.getByTestId("celebrate-btn")).toBeInTheDocument();
  });

  it("shows the per-reaction count next to each button", () => {
    renderCard();

    expect(screen.getByTestId("like-btn")).toHaveTextContent("1.2k");
    expect(screen.getByTestId("love-btn")).toHaveTextContent("50");
    expect(screen.getByTestId("celebrate-btn")).toHaveTextContent("20");
  });

  it("fires onReaction with LOVE when the love button is clicked", () => {
    const handleReaction = vi.fn();
    renderCard({ onReaction: handleReaction });

    fireEvent.click(screen.getByTestId("love-btn"));
    expect(handleReaction).toHaveBeenCalledWith("post-1", "LOVE");
  });

  it("fires onReaction with CELEBRATE when the celebrate button is clicked", () => {
    const handleReaction = vi.fn();
    renderCard({ onReaction: handleReaction });

    fireEvent.click(screen.getByTestId("celebrate-btn"));
    expect(handleReaction).toHaveBeenCalledWith("post-1", "CELEBRATE");
  });

  it("marks the active reaction button with aria-pressed=true and the others with false", () => {
    renderCard();

    expect(screen.getByTestId("like-btn")).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(screen.getByTestId("love-btn")).toHaveAttribute(
      "aria-pressed",
      "false",
    );
    expect(screen.getByTestId("celebrate-btn")).toHaveAttribute(
      "aria-pressed",
      "false",
    );
  });

  it("uses aria-pressed=false on all reaction buttons when the user has no reaction", () => {
    renderCard({ post: { ...MOCK_POST, userReaction: undefined } });

    expect(screen.getByTestId("like-btn")).toHaveAttribute(
      "aria-pressed",
      "false",
    );
    expect(screen.getByTestId("love-btn")).toHaveAttribute(
      "aria-pressed",
      "false",
    );
    expect(screen.getByTestId("celebrate-btn")).toHaveAttribute(
      "aria-pressed",
      "false",
    );
  });

  it("disables the three reaction buttons and sets aria-busy when isReactionPending is true", () => {
    renderCard({ isReactionPending: true });

    for (const testId of ["like-btn", "love-btn", "celebrate-btn"]) {
      const btn = screen.getByTestId(testId);
      expect(btn).toBeDisabled();
      expect(btn).toHaveAttribute("aria-busy", "true");
    }
  });

  it("keeps reaction buttons enabled by default", () => {
    renderCard();

    expect(screen.getByTestId("like-btn")).not.toBeDisabled();
    expect(screen.getByTestId("love-btn")).not.toBeDisabled();
    expect(screen.getByTestId("celebrate-btn")).not.toBeDisabled();
  });

  it("still fires onReaction for non-active reaction buttons when the user has another reaction set", () => {
    const handleReaction = vi.fn();
    // user has LOVE active, clicking CELEBRATE should still call onReaction.
    renderCard({
      post: { ...MOCK_POST, userReaction: "LOVE" },
      onReaction: handleReaction,
    });

    expect(screen.getByTestId("love-btn")).toHaveAttribute(
      "aria-pressed",
      "true",
    );

    fireEvent.click(screen.getByTestId("celebrate-btn"));
    expect(handleReaction).toHaveBeenCalledWith("post-1", "CELEBRATE");
  });

  it("falls back to a count of 0 when a reaction type is missing from the post", () => {
    const postMissingCounts: Post = {
      ...MOCK_POST,
      reactions: {
        LIKE: 0,
        LOVE: 0,
        CELEBRATE: 0,
        RETWEET: 0,
      } as Post["reactions"],
    };
    renderCard({ post: postMissingCounts });

    expect(screen.getByTestId("like-btn")).toHaveTextContent("0");
    expect(screen.getByTestId("love-btn")).toHaveTextContent("0");
    expect(screen.getByTestId("celebrate-btn")).toHaveTextContent("0");
  });
});
