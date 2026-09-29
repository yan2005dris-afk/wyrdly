import type { UserProfileSummary } from "../../../types/domain";

export type ProfileTabId = "posts" | "followers" | "following" | "reactions";

export interface ProfileHeaderCardProps {
  readonly user: UserProfileSummary;
  readonly coverUrl?: string;
  readonly isCurrentUser?: boolean;
  readonly isSubscribed?: boolean;
  readonly activeTab: ProfileTabId;
  readonly onTabChange: (tab: ProfileTabId) => void;
  readonly onMessageClick?: () => void;
  readonly onSubscribeToggle?: () => void;
  readonly onEditProfileClick?: () => void;
  readonly className?: string;
}
