export interface PushPermissionBannerProps {
  readonly onEnable: () => Promise<void> | void;
  readonly onDismiss: () => void;
  readonly isLoading?: boolean;
  readonly className?: string;
  /**
   * Why push notifications are unavailable. Drives the message shown to the
   * user so we don't just say "Enable" when the real blocker is HTTP, denied
   * permission, or a browser without the required APIs.
   */
  readonly reason?: "unsupported" | "denied" | "insecure-context" | "missing-cache";
}
