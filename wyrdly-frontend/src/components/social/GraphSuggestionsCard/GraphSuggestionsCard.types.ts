import type { GraphSuggestionUser } from "../../../types/domain";

export interface GraphSuggestionsCardProps {
  readonly suggestions: readonly GraphSuggestionUser[];
  readonly onFollowToggle: (userId: string) => void;
  readonly onSeeAllClick?: () => void;
  readonly className?: string;
}
