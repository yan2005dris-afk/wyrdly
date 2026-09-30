import { useState, type FC } from "react";
import { Link } from "react-router-dom";
import { Loader2 } from "lucide-react";
import { Avatar } from "../../ui/Avatar";
import { useFollow } from "../../../hooks/useFollow";
import styles from "./UserListRow.module.css";

export interface UserListRowUser {
  readonly id: string;
  readonly username: string;
  readonly fullName: string;
  readonly avatarUrl?: string | null;
  readonly isFollowing: boolean;
}

export interface UserListRowProps {
  readonly user: UserListRowUser;
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

  // Optimistic local state: starts from the server-side `isFollowing`
  // flag, flips on click, rolls back if the API rejects the call.
  const [following, setFollowing] = useState<boolean>(user.isFollowing);
  const [original, setOriginal] = useState<boolean>(user.isFollowing);

  const handleToggle = async () => {
    const next = !following;
    setFollowing(next);
    try {
      if (next) {
        await follow(user.id);
      } else {
        await unfollow(user.id);
      }
      onAfterToggle?.(user.id);
    } catch (err) {
      // Roll back to the server-known state.
      setFollowing(original);
      console.error("Follow toggle failed", err);
    }
  };

  // If the parent re-fetches and the server-side flag changes, sync
  // our local state so the UI stays consistent.
  if (user.isFollowing !== original && !isMutating) {
    setOriginal(user.isFollowing);
    setFollowing(user.isFollowing);
  }

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
