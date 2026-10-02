import { render, screen, fireEvent } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ChatInputBar } from "./ChatInputBar";

describe("ChatInputBar Component", () => {
  it("renders input placeholder with recipient name", () => {
    render(<ChatInputBar recipientName="Alice" onSend={vi.fn()} />);

    expect(screen.getByPlaceholderText("Message Alice...")).toBeInTheDocument();
  });

  it("disables send button when input is empty", () => {
    render(<ChatInputBar onSend={vi.fn()} />);

    const sendBtn = screen.getByTestId("chat-send-btn");
    expect(sendBtn).toBeDisabled();
  });

  it("enables send button on typing and triggers onSend on form submit", () => {
    const handleSend = vi.fn();
    render(<ChatInputBar onSend={handleSend} />);

    const input = screen.getByRole("textbox");
    fireEvent.change(input, { target: { value: "Hello Alice!" } });

    const sendBtn = screen.getByTestId("chat-send-btn");
    expect(sendBtn).toBeEnabled();

    fireEvent.click(sendBtn);
    expect(handleSend).toHaveBeenCalledWith("Hello Alice!", undefined);
  });
});
