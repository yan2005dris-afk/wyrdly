import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { MessageBubble } from "./MessageBubble";
import type { ChatMessage } from "../../../types/chat";

const INCOMING_MSG: ChatMessage = {
  id: "msg-1",
  senderId: "user-alice",
  recipientId: "user-maya",
  text: "Did you see the new relay map?",
  timestamp: "10:24 AM",
  deliveryStatus: "DELIVERED",
  isEncrypted: true,
};

const OUTGOING_MSG: ChatMessage = {
  id: "msg-2",
  senderId: "user-maya",
  recipientId: "user-alice",
  text: "Yes! 42ms hop from ap-south-1.",
  timestamp: "10:26 AM",
  deliveryStatus: "READ",
  isEncrypted: true,
};

describe("MessageBubble Component", () => {
  it("renders incoming message with sender avatar and timestamp", () => {
    render(
      <MessageBubble
        message={INCOMING_MSG}
        isOutgoing={false}
        senderName="Alice Chen"
      />,
    );

    expect(
      screen.getByText("Did you see the new relay map?"),
    ).toBeInTheDocument();
    expect(screen.getByText("10:24 AM")).toBeInTheDocument();
    expect(screen.getByTestId("avatar-fallback")).toBeInTheDocument();
  });

  it("renders outgoing message with delivery checkmark", () => {
    render(<MessageBubble message={OUTGOING_MSG} isOutgoing={true} />);

    expect(
      screen.getByText("Yes! 42ms hop from ap-south-1."),
    ).toBeInTheDocument();
    expect(screen.getByText("10:26 AM")).toBeInTheDocument();
    expect(screen.getByTestId("status-read")).toBeInTheDocument();
  });
});
