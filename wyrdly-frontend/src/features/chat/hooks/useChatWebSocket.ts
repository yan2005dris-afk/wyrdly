import { useEffect, useRef, useState, useCallback } from "react";
import type { MessageResponse, WebSocketIncomingMessage } from "../types";

export interface UseChatWebSocketOptions {
  token: string | null;
  onMessageReceived?: (message: MessageResponse) => void;
  onUserTyping?: (userId: string) => void;
}

export const useChatWebSocket = ({
  token,
  onMessageReceived,
  onUserTyping,
}: UseChatWebSocketOptions) => {
  const [isConnected, setIsConnected] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const socketRef = useRef<WebSocket | null>(null);

  useEffect(() => {
    if (!token) {
      if (socketRef.current) {
        socketRef.current.close();
        socketRef.current = null;
      }
      return () => {};
    }

    const wsProtocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const host = window.location.hostname;
    const port = window.location.port ? `:${window.location.port}` : "";
    const wsUrl = `${wsProtocol}//${host}${port}/ws/chat?token=${encodeURIComponent(token)}`;

    const ws = new WebSocket(wsUrl);
    socketRef.current = ws;

    ws.onopen = () => {
      setIsConnected(true);
      setError(null);
    };

    ws.onmessage = (event) => {
      try {
        const payload: WebSocketIncomingMessage = JSON.parse(event.data);
        if (
          payload.action === "NEW_MESSAGE" ||
          payload.action === "MESSAGE_RECEIVED"
        ) {
          if (typeof payload.message === "object" && payload.message !== null) {
            onMessageReceived?.(payload.message as MessageResponse);
          }
        } else if (payload.action === "USER_TYPING") {
          if (payload.userId) {
            onUserTyping?.(payload.userId);
          }
        }
      } catch {
        // Ignored or invalid json frame
      }
    };

    ws.onerror = () => {
      setError("WebSocket connection error");
    };

    ws.onclose = (event) => {
      setIsConnected(false);
      if (event.code === 4401) {
        setError("Unauthorized token (close code 4401)");
      }
    };

    return () => {
      ws.close();
      socketRef.current = null;
      setIsConnected(false);
    };
  }, [token, onMessageReceived, onUserTyping]);

  const sendMessage = useCallback((recipientId: string, content: string) => {
    if (socketRef.current && socketRef.current.readyState === WebSocket.OPEN) {
      const payload = {
        action: "SEND_MESSAGE",
        recipientId,
        content,
      };
      socketRef.current.send(JSON.stringify(payload));
      return true;
    }
    return false;
  }, []);

  const sendTyping = useCallback((recipientId: string) => {
    if (socketRef.current && socketRef.current.readyState === WebSocket.OPEN) {
      const payload = {
        action: "TYPING",
        recipientId,
      };
      socketRef.current.send(JSON.stringify(payload));
    }
  }, []);

  return {
    isConnected,
    error,
    sendMessage,
    sendTyping,
  };
};
