import type { UserProfileSummary } from "../../../types/domain";

export interface ChatHeaderProps {
  readonly participant: UserProfileSummary;
  readonly isOnline?: boolean;
  readonly statusText?: string;
  readonly onCallClick?: () => void;
  readonly onVideoClick?: () => void;
  readonly onOptionsClick?: () => void;
  readonly className?: string;
}
