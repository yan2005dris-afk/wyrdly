import {
  useState,
  useRef,
  type FC,
  type FormEvent,
  type KeyboardEvent,
  type ChangeEvent,
} from "react";
import { Paperclip, Smile, Send } from "lucide-react";
import type { ChatInputBarProps } from "./ChatInputBar.types";
import styles from "./ChatInputBar.module.css";

export const ChatInputBar: FC<ChatInputBarProps> = ({
  recipientName = "Alice",
  isSending = false,
  onSend,
  onEmojiClick,
  className = "",
}) => {
  const [text, setText] = useState("");
  const [selectedFile, setSelectedFile] = useState<File | undefined>();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleFileChange = (e: ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      setSelectedFile(e.target.files[0]);
    }
  };

  const handleFormSubmit = async (e?: FormEvent) => {
    e?.preventDefault();
    if (!text.trim() && !selectedFile) return;

    await onSend(text.trim(), selectedFile);
    setText("");
    setSelectedFile(undefined);
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      void handleFormSubmit();
    }
  };

  return (
    <form
      onSubmit={handleFormSubmit}
      className={`${styles.container} ${className}`}
      data-testid="chat-input-bar"
    >
      <input
        type="file"
        ref={fileInputRef}
        onChange={handleFileChange}
        className={styles.hiddenFileInput}
        data-testid="chat-file-input"
      />
      <button
        type="button"
        onClick={() => fileInputRef.current?.click()}
        className={styles.clipButton}
        aria-label="Attach file"
        data-testid="chat-attach-btn"
      >
        <Paperclip className="w-4 h-4" />
      </button>

      <div className={styles.inputWrapper}>
        <input
          type="text"
          placeholder={`Message ${recipientName}...`}
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={handleKeyDown}
          className={styles.input}
          data-testid="chat-message-input"
        />

        {onEmojiClick && (
          <button
            type="button"
            onClick={onEmojiClick}
            className={styles.emojiButton}
            aria-label="Insert emoji"
            data-testid="chat-emoji-btn"
          >
            <Smile className="w-4 h-4" />
          </button>
        )}
      </div>

      <button
        type="submit"
        disabled={(!text.trim() && !selectedFile) || isSending}
        className={styles.sendButton}
        aria-label="Send message"
        data-testid="chat-send-btn"
      >
        <Send className="w-4 h-4" />
      </button>
    </form>
  );
};
