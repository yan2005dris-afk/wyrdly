import type { FC } from "react";
import { Link } from "react-router-dom";
import { GitBranch } from "lucide-react";
import type { GraphSuggestionsCardProps } from "./GraphSuggestionsCard.types";
import { Avatar } from "../../ui/Avatar";
import styles from "./GraphSuggestionsCard.module.css";

export const GraphSuggestionsCard: FC<GraphSuggestionsCardProps> = ({
  suggestions,
  onFollowToggle,
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
          <div
            key={user.id}
            className={styles.item}
            data-testid={`suggestion-item-${user.id}`}
          >
            <div className={styles.userMeta}>
              <Link to={`/profile/${user.username}`}>
                <Avatar src={user.avatarUrl} alt={user.fullName} size="sm" />
              </Link>
              <div className={styles.nameCol}>
                <Link
                  to={`/profile/${user.username}`}
                  className={styles.userName}
                  title={user.fullName}
                >
                  {user.fullName}
                </Link>
                <span
                  className={styles.mutualSnippet}
                  title={user.mutualConnectionSnippet}
                >
                  {user.mutualConnectionSnippet}
                </span>
              </div>
            </div>

            <button
              type="button"
              onClick={() => onFollowToggle(user.id)}
              className={`${styles.followBtn} ${
                user.isFollowing ? styles.followingActive : ""
              }`}
              data-testid={`follow-btn-${user.id}`}
            >
              {user.isFollowing ? "Following" : "Follow"}
            </button>
          </div>
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
