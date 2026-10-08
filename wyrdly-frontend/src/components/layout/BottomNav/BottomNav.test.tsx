import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { BottomNav } from "./BottomNav";

describe("BottomNav", () => {
  it("renders mobile navigation items with correct destinations", () => {
    render(
      <MemoryRouter initialEntries={["/feed"]}>
        <BottomNav profileUsername="alice" />
      </MemoryRouter>,
    );

    expect(screen.getByTestId("bottom-nav")).toBeInTheDocument();
    expect(screen.getByTestId("bottom-nav-feed")).toHaveAttribute(
      "href",
      "/feed",
    );
    expect(screen.getByTestId("bottom-nav-explore")).toHaveAttribute(
      "href",
      "/explore",
    );
    expect(screen.getByTestId("bottom-nav-chat")).toHaveAttribute(
      "href",
      "/chat",
    );
    expect(screen.getByTestId("bottom-nav-profile")).toHaveAttribute(
      "href",
      "/profile/alice",
    );
  });

  it("displays unread chat badge when unreadMessagesCount > 0", () => {
    render(
      <MemoryRouter>
        <BottomNav unreadMessagesCount={5} />
      </MemoryRouter>,
    );

    const badge = screen.getByTestId("bottom-nav-chat-badge");
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveTextContent("5");
  });

  it("caps unread badge text at 99+ when count is large", () => {
    render(
      <MemoryRouter>
        <BottomNav unreadMessagesCount={150} />
      </MemoryRouter>,
    );

    const badge = screen.getByTestId("bottom-nav-chat-badge");
    expect(badge).toHaveTextContent("99+");
  });

  it("invokes onNewPostClick when plus button is clicked", () => {
    const onNewPost = vi.fn();
    render(
      <MemoryRouter>
        <BottomNav onNewPostClick={onNewPost} />
      </MemoryRouter>,
    );

    const btn = screen.getByTestId("bottom-nav-new-post");
    fireEvent.click(btn);
    expect(onNewPost).toHaveBeenCalledTimes(1);
  });
});
