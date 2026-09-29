import { type FC, type MouseEvent } from "react";
import { Link } from "react-router-dom";
import { Avatar } from "../../ui/Avatar";
import { Button } from "../../ui/Button";
import type { UserSearchResultCardProps } from "./UserSearchResultCard.types";
import styles from "./UserSearchResultCard.module.css";

export const UserSearchResultCard: FC<UserSearchResultCardProps> = ({
  result,
  onFollowToggle,
}) => {
  const handleFollowClick = (event: MouseEvent<HTMLButtonElement>) => {
    event.preventDefault();
    event.stopPropagation();
    onFollowToggle(result.id);
  };

  return (
    <Link
      to={`/profile/${result.username}`}
      className={styles.card}
      data-testid={`user-search-result-${result.id}`}
    >
      <div className={styles.avatar}>
        <Avatar
          src={result.avatarUrl ?? undefined}
          alt={result.fullName || result.username}
          size="md"
        />
      </div>
      <div className={styles.identity}>
        <p
          className={styles.username}
          data-testid={`user-search-username-${result.id}`}
        >
          @{result.username}
        </p>
        <p
          className={styles.fullName}
          data-testid={`user-search-fullname-${result.id}`}
        >
          {result.fullName}
        </p>
        {result.bio && (
          <p
            className={styles.bio}
            data-testid={`user-search-bio-${result.id}`}
          >
            {result.bio}
          </p>
        )}
        {result.mutualConnectionSnippet && (
          <p
            className={styles.mutual}
            data-testid={`user-search-mutual-${result.id}`}
          >
            {result.mutualConnectionSnippet}
          </p>
        )}
      </div>
      <div className={styles.actions}>
        <Button
          variant={result.isFollowing ? "secondary" : "primary"}
          size="sm"
          onClick={handleFollowClick}
          data-testid={`user-search-follow-btn-${result.id}`}
        >
          {result.isFollowing ? "Following" : "Follow"}
        </Button>
      </div>
    </Link>
  );
};
