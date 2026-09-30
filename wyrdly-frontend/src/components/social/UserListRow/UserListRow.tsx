import { useState, type FC } from "react";
import { Link } from "react-router-dom";
import { Loader2 } from "lucide-react";
import type { ProfileUserSummary } from "../../../types/suggestions";
import { Avatar } from "../../ui/Avatar";
import { useFollow } from "../../../hooks/useFollow";
import styles from "./UserListRow.module.css";

export interface UserListRowProps {
  readonly user: ProfileUserSummary;
  /**
   * Optional one-line subtitle shown under the user's name. Smart
   * Suggestions passes the mutual-connection snippet; followers /
   * following tabs leave it undefined.
   */
  readonly subtitle?: string;
  /**
   * Optional callback fired after a follow/unfollow attempt succeeds.
   * Lets parents invalidate caches or refetch sibling stats (e.g. the
   * follower count on the profile header).
   */
  readonly onAfterToggle?: (userId: string) => void;
  readonly className?: string;
}

/**
 * A single row in a user list: avatar + identity + Follow/Following
 * toggle. Owns its own follow mutation via `useFollow()` so callers
 * don't need to wire a click handler.
 *
 * The button flips optimistically the moment the user clicks; if the
 * backend rejects the call the state rolls back and the failure is
 * surfaced via console.error (no UI toast yet — that is a separate
 * ticket).
 */
export const UserListRow: FC<UserListRowProps> = ({
  user,
  subtitle,
  onAfterToggle,
  className = "",
}) => {
  const { follow, unfollow, isMutating } = useFollow();

  // Optimistic override: null = trust the server-side `isFollowing`,
  // any other value = the local optimistic flip the user clicked. The
  // rendered value is `optimistic ?? user.isFollowing`, which means
  // we never need an effect to sync local state from props and the
  // React 19 set-state-in-effect rule stays happy.
  const [optimistic, setOptimistic] = useState<boolean | null>(null);
  const following = optimistic ?? user.isFollowing;

  const handleToggle = async () => {
    const next = !following;
    setOptimistic(next);
    try {
      if (next) {
        await follow(user.id);
      } else {
        await unfollow(user.id);
      }
      onAfterToggle?.(user.id);
    } catch (err) {
      // Roll back to the server-known state.
      setOptimistic(null);
      console.error("Follow toggle failed", err);
    }
  };

  const buttonClass = [
    styles.followBtn,
    following ? styles.followingActive : "",
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <div
      className={`${styles.row} ${className}`}
      data-testid={`user-list-row-${user.id}`}
    >
      <Link
        to={`/profile/${user.username}`}
        className={styles.identity}
        data-testid={`user-list-row-${user.id}-link`}
      >
        <Avatar
          src={user.avatarUrl ?? undefined}
          alt={user.fullName}
          size="sm"
        />
        <div className={styles.text}>
          <div className={styles.name} title={user.fullName}>
            {user.fullName}
          </div>
          <div className={styles.username}>@{user.username}</div>
          {subtitle && (
            <div className={styles.subtitle} title={subtitle}>
              {subtitle}
            </div>
          )}
        </div>
      </Link>

      <button
        type="button"
        onClick={handleToggle}
        disabled={isMutating}
        className={buttonClass}
        data-testid={`follow-toggle-${user.id}`}
        data-following={following}
      >
        {isMutating ? (
          <Loader2 className={styles.spinner} data-testid="follow-spinner" />
        ) : following ? (
          "Following"
        ) : (
          "Follow"
        )}
      </button>
    </div>
  );
};
