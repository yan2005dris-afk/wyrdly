import type { UserProfileSummary } from "../../../../types/domain";

export interface UserSummaryCardProps {
  readonly user: UserProfileSummary;
  readonly showStats?: boolean;
  readonly className?: string;
  readonly onProfileClick?: (username: string) => void;
}
