import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { BrowserRouter } from "react-router-dom";
import { ChatPage } from "./ChatPage";
import { AuthProvider } from "../context/AuthContext";

const renderChatPage = () => {
  window.HTMLElement.prototype.scrollIntoView = vi.fn();

  return render(
    <AuthProvider>
      <BrowserRouter>
        <ChatPage />
      </BrowserRouter>
    </AuthProvider>,
  );
};

describe("ChatPage Component", () => {
  it("renders conversation list and active chat window", () => {
    renderChatPage();

    expect(screen.getByTestId("chat-page")).toBeInTheDocument();
    expect(screen.getByTestId("conversation-list")).toBeInTheDocument();
    expect(screen.getByTestId("chat-window")).toBeInTheDocument();
    expect(screen.getAllByText("Alice Chen").length).toBeGreaterThanOrEqual(1);
  });

  it("sends a new message in the chat", async () => {
    renderChatPage();

    const input = screen.getByPlaceholderText("Message Alice...");
    fireEvent.change(input, { target: { value: "Hello from Vitest test!" } });

    const sendBtn = screen.getByTestId("chat-send-btn");
    fireEvent.click(sendBtn);

    await waitFor(() => {
      expect(
        screen.getAllByText("Hello from Vitest test!").length,
      ).toBeGreaterThanOrEqual(1);
    });
  });
});
