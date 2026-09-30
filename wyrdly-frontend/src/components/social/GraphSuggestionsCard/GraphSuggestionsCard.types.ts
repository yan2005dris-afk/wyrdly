import type { GraphSuggestionUser } from "../../../types/domain";

export interface GraphSuggestionsCardProps {
  readonly suggestions: readonly GraphSuggestionUser[];
  readonly onSeeAllClick?: () => void;
  readonly onAfterToggle?: (userId: string) => void;
  readonly className?: string;
}
