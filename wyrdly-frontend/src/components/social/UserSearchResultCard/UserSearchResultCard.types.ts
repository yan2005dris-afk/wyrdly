import type { UserSearchResult } from "../../../types/userSearch";

export interface UserSearchResultCardProps {
  readonly result: UserSearchResult;
  readonly onFollowToggle: (userId: string) => void;
}
