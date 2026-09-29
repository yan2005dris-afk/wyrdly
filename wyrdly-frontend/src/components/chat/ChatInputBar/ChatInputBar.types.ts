export interface ChatInputBarProps {
  readonly recipientName?: string;
  readonly isSending?: boolean;
  readonly onSend: (
    text: string,
    attachmentFile?: File,
  ) => Promise<void> | void;
  readonly onEmojiClick?: () => void;
  readonly className?: string;
}
