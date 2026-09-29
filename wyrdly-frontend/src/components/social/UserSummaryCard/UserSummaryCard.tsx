import type { FC } from "react";
import { Link } from "react-router-dom";
import { CheckCircle } from "lucide-react";
import type { UserSummaryCardProps } from "./UserSummaryCard.types";
import { Avatar } from "../../ui/Avatar";
import styles from "./UserSummaryCard.module.css";

const formatStatNumber = (num: number): string => {
  if (num >= 1_000_000) {
    return (num / 1_000_000).toFixed(1).replace(/\.0$/, "") + "M";
  }
  if (num >= 1_000) {
    return (num / 1_000).toFixed(1).replace(/\.0$/, "") + "k";
  }
  return num.toString();
};

export const UserSummaryCard: FC<UserSummaryCardProps> = ({
  user,
  className = "",
  onProfileClick,
}) => {
  const profileUrl = `/profile/${user.username}`;

  return (
    <div
      className={`${styles.card} ${className}`}
      data-testid="user-summary-card"
    >
      <div className={styles.profileHeader}>
        <Link to={profileUrl} onClick={() => onProfileClick?.(user.username)}>
          <Avatar
            src={user.avatarUrl}
            alt={user.fullName}
            size="lg"
            showRing={true}
            ringColor="indigo"
          />
        </Link>

        <div>
          <div className={styles.nameRow}>
            <Link
              to={profileUrl}
              className={styles.fullName}
              onClick={() => onProfileClick?.(user.username)}
            >
              {user.fullName}
            </Link>
            {user.isVerified && (
              <span
                className={styles.verifiedIcon}
                data-testid="verified-badge"
              >
                <CheckCircle className="w-3.5 h-3.5 fill-indigo-600 text-white" />
              </span>
            )}
          </div>
          <p className={styles.handleText}>
            @{user.username} • {user.instanceUrl || "federated"}
          </p>
        </div>
      </div>

      <div className={styles.statsGrid}>
        <div>
          <span className={styles.statValue} data-testid="followers-stat">
            {formatStatNumber(user.stats.followersCount)}
          </span>
          <p className={styles.statLabel}>Followers</p>
        </div>
        <div>
          <span className={styles.statValue} data-testid="following-stat">
            {formatStatNumber(user.stats.followingCount)}
          </span>
          <p className={styles.statLabel}>Following</p>
        </div>
        <div>
          <span className={styles.statValue} data-testid="posts-stat">
            {formatStatNumber(user.stats.postsCount)}
          </span>
          <p className={styles.statLabel}>Posts</p>
        </div>
      </div>
    </div>
  );
};
