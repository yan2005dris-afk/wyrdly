import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { BrowserRouter } from "react-router-dom";
import { AppNavbar } from "./AppNavbar";
import type { UserProfileSummary } from "../../../types/domain";

const MOCK_USER: UserProfileSummary = {
  id: "user-1",
  username: "maya",
  fullName: "Maya Krishnan",
  avatarUrl: "https://example.com/maya.jpg",
  isVerified: true,
  instanceUrl: "relaymesh.io",
  stats: {
    followersCount: 12400,
    followingCount: 890,
    postsCount: 3100,
  },
};

const renderWithRouter = (ui: React.ReactElement) => {
  return render(<BrowserRouter>{ui}</BrowserRouter>);
};

describe("AppNavbar Component", () => {
  it("renders brand logo and title in default mode", () => {
    renderWithRouter(<AppNavbar currentUser={MOCK_USER} />);

    expect(screen.getByText("Relaymesh")).toBeInTheDocument();
    expect(screen.getByText("Distributed Social")).toBeInTheDocument();
  });

  it("renders back button when backTo is provided", () => {
    renderWithRouter(<AppNavbar backTo="/feed" backLabel="Back to feed" />);

    expect(screen.getByText("Back to feed")).toBeInTheDocument();
    expect(screen.getByTestId("navbar-back-link")).toHaveAttribute(
      "href",
      "/feed",
    );
  });

  it("renders search input and handles search query input", () => {
    const handleSearch = vi.fn();
    renderWithRouter(
      <AppNavbar
        showSearch={true}
        searchQuery=""
        onSearchChange={handleSearch}
      />,
    );

    const input = screen.getByPlaceholderText(
      "Search people, relays, hashtags...",
    );
    fireEvent.change(input, { target: { value: "neo4j" } });

    expect(handleSearch).toHaveBeenCalledWith("neo4j");
  });

  it("renders federated status badge with ping animation", () => {
    renderWithRouter(
      <AppNavbar statusVariant="federated" statusText="Federated • Live" />,
    );

    expect(screen.getByText("Federated • Live")).toBeInTheDocument();
  });

  it("renders unread notifications badge and triggers click callback", () => {
    const handleNotificationClick = vi.fn();
    renderWithRouter(
      <AppNavbar
        unreadNotificationsCount={9}
        onNotificationsClick={handleNotificationClick}
      />,
    );

    const btn = screen.getByTestId("navbar-notifications-btn");
    expect(screen.getByText("9")).toBeInTheDocument();

    fireEvent.click(btn);
    expect(handleNotificationClick).toHaveBeenCalledTimes(1);
  });

  it("renders notification popover slot when open", () => {
    renderWithRouter(
      <AppNavbar
        isNotificationsOpen={true}
        notificationsSlot={
          <div data-testid="test-popover">Popover Content</div>
        }
      />,
    );

    expect(screen.getByTestId("test-popover")).toBeInTheDocument();
  });

  it("opens the profile menu with both items when the trigger is clicked", () => {
    renderWithRouter(<AppNavbar currentUser={MOCK_USER} onLogout={vi.fn()} />);

    expect(screen.queryByTestId("navbar-profile-menu")).not.toBeInTheDocument();

    fireEvent.click(screen.getByTestId("navbar-profile-menu-btn"));

    expect(screen.getByTestId("navbar-profile-menu")).toBeInTheDocument();
    expect(screen.getByTestId("navbar-profile-link-menu")).toHaveAttribute(
      "href",
      "/profile/maya",
    );
    expect(screen.getByText("View profile")).toBeInTheDocument();
    expect(screen.getByTestId("navbar-logout-btn")).toHaveTextContent(
      "Log out",
    );
  });

  it("calls onLogout and closes the menu when Log out is clicked", () => {
    const handleLogout = vi.fn();
    renderWithRouter(
      <AppNavbar currentUser={MOCK_USER} onLogout={handleLogout} />,
    );

    fireEvent.click(screen.getByTestId("navbar-profile-menu-btn"));
    fireEvent.click(screen.getByTestId("navbar-logout-btn"));

    expect(handleLogout).toHaveBeenCalledTimes(1);
    expect(screen.queryByTestId("navbar-profile-menu")).not.toBeInTheDocument();
  });

  it("closes the profile menu on Escape", () => {
    renderWithRouter(<AppNavbar currentUser={MOCK_USER} onLogout={vi.fn()} />);

    fireEvent.click(screen.getByTestId("navbar-profile-menu-btn"));
    expect(screen.getByTestId("navbar-profile-menu")).toBeInTheDocument();

    fireEvent.keyDown(document, { key: "Escape" });

    expect(screen.queryByTestId("navbar-profile-menu")).not.toBeInTheDocument();
  });

  it("closes the profile menu on outside mousedown", () => {
    renderWithRouter(<AppNavbar currentUser={MOCK_USER} onLogout={vi.fn()} />);

    fireEvent.click(screen.getByTestId("navbar-profile-menu-btn"));
    expect(screen.getByTestId("navbar-profile-menu")).toBeInTheDocument();

    fireEvent.mouseDown(document.body);

    expect(screen.queryByTestId("navbar-profile-menu")).not.toBeInTheDocument();
  });

  it("does not render the profile menu trigger when onLogout is omitted", () => {
    renderWithRouter(<AppNavbar currentUser={MOCK_USER} />);

    expect(
      screen.queryByTestId("navbar-profile-menu-btn"),
    ).not.toBeInTheDocument();
    expect(screen.queryByTestId("navbar-profile-menu")).not.toBeInTheDocument();
    expect(screen.getByTestId("navbar-profile-link")).toBeInTheDocument();
  });
});
