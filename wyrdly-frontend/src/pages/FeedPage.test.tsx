import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { BrowserRouter } from "react-router-dom";
import { FeedPage } from "./FeedPage";
import { AuthProvider } from "../context/AuthContext";

const renderFeedPage = () => {
  return render(
    <AuthProvider>
      <BrowserRouter>
        <FeedPage />
      </BrowserRouter>
    </AuthProvider>,
  );
};

describe("FeedPage Component", () => {
  it("renders post composer and feed timeline", () => {
    renderFeedPage();

    expect(screen.getByTestId("feed-page")).toBeInTheDocument();
    expect(screen.getByTestId("create-post-card")).toBeInTheDocument();
    expect(screen.getByText("For you (Graph Feed)")).toBeInTheDocument();
    expect(screen.getByText("Jonas Weber")).toBeInTheDocument();
    expect(screen.getAllByText("Alice Chen").length).toBeGreaterThanOrEqual(1);
  });

  it("publishes a new post to the timeline", async () => {
    renderFeedPage();

    const textarea = screen.getByPlaceholderText(
      "Share an update with your federated graph...",
    );
    fireEvent.change(textarea, { target: { value: "Testing post publish" } });

    const publishBtn = screen.getByRole("button", { name: /publish/i });
    fireEvent.click(publishBtn);

    await waitFor(() => {
      expect(
        screen.getAllByText("Testing post publish").length,
      ).toBeGreaterThanOrEqual(1);
    });
  });
});
