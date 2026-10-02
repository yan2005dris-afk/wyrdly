import type { FC } from "react";
import { GitBranch } from "lucide-react";
import type { GraphSuggestionsCardProps } from "./GraphSuggestionsCard.types";
import { UserListRow } from "../UserListRow";
import styles from "./GraphSuggestionsCard.module.css";

export const GraphSuggestionsCard: FC<GraphSuggestionsCardProps> = ({
  suggestions,
  onAfterToggle,
  onSeeAllClick,
  className = "",
}) => {
  return (
    <div
      className={`${styles.card} ${className}`}
      data-testid="graph-suggestions-card"
    >
      <div className={styles.header}>
        <h3 className={styles.title}>Smart Suggestions</h3>
        <span className={styles.graphBadge}>
          <GitBranch className="w-3 h-3" />
          <span>Graph-based</span>
        </span>
      </div>

      <div className={styles.list}>
        {suggestions.map((user) => (
          <UserListRow
            key={user.id}
            user={user}
            subtitle={user.mutualConnectionSnippet}
            onAfterToggle={onAfterToggle}
          />
        ))}
      </div>

      {onSeeAllClick && (
        <button
          type="button"
          onClick={onSeeAllClick}
          className={styles.footerLink}
          data-testid="see-all-suggestions-btn"
        >
          See all recommendations
        </button>
      )}
    </div>
  );
};
