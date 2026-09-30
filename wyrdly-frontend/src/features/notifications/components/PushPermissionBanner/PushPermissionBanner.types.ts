export interface PushPermissionBannerProps {
  readonly onEnable: () => Promise<void> | void;
  readonly onDismiss: () => void;
  readonly isLoading?: boolean;
  readonly className?: string;
}
