import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { BrowserRouter } from "react-router-dom";
import { SidebarNav } from "./SidebarNav";

const renderWithRouter = (ui: React.ReactElement) => {
  return render(<BrowserRouter>{ui}</BrowserRouter>);
};

describe("SidebarNav Component", () => {
  it("renders all navigation links without redundant profile item", () => {
    renderWithRouter(<SidebarNav />);

    expect(screen.getByText("Feed")).toBeInTheDocument();
    expect(screen.getByText("Explore")).toBeInTheDocument();
    expect(screen.getByText("Messages")).toBeInTheDocument();
    expect(screen.getByText("Alerts")).toBeInTheDocument();
    expect(screen.queryByTestId("nav-link-profile")).not.toBeInTheDocument();
  });

  it("renders message and alert badge counts", () => {
    renderWithRouter(
      <SidebarNav unreadMessagesCount={4} unreadAlertsCount={12} />,
    );

    expect(screen.getByTestId("nav-badge-messages")).toHaveTextContent("4");
    expect(screen.getByTestId("nav-badge-alerts")).toHaveTextContent("9+");
  });

  it("renders New Post button and fires callback on click", () => {
    const handleNewPost = vi.fn();
    renderWithRouter(<SidebarNav onNewPostClick={handleNewPost} />);

    const button = screen.getByRole("button", { name: /new post/i });
    expect(button).toBeInTheDocument();
    fireEvent.click(button);
    expect(handleNewPost).toHaveBeenCalledTimes(1);
  });

  it("does not render mock relay telemetry or node status card", () => {
    renderWithRouter(<SidebarNav />);

    expect(screen.queryByTestId("sidebar-node-status")).not.toBeInTheDocument();
    expect(screen.queryByText(/relays connected/i)).not.toBeInTheDocument();
  });

  it("renders the log out button only when onLogout is provided", () => {
    const { unmount } = renderWithRouter(<SidebarNav />);
    expect(screen.queryByTestId("nav-logout-btn")).not.toBeInTheDocument();
    unmount();

    renderWithRouter(<SidebarNav onLogout={vi.fn()} />);
    expect(screen.getByTestId("nav-logout-btn")).toBeInTheDocument();
  });

  it("fires the onLogout callback when the log out button is clicked", () => {
    const handleLogout = vi.fn();
    renderWithRouter(<SidebarNav onLogout={handleLogout} />);

    const button = screen.getByTestId("nav-logout-btn");
    expect(button).toHaveTextContent("Log out");
    expect(button).toHaveAttribute("type", "button");
    expect(button.tagName).toBe("BUTTON");

    fireEvent.click(button);
    expect(handleLogout).toHaveBeenCalledTimes(1);
  });
});
