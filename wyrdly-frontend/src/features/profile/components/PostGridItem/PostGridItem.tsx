import type { FC } from "react";
import type { PostGridItemProps } from "./PostGridItem.types";
import { AuthImage } from "../../../../components/ui/AuthImage";
import { Skeleton } from "../../../../components/ui/Skeleton";
import styles from "./PostGridItem.module.css";

export const PostGridItemSkeleton: FC<{ className?: string }> = ({
  className = "",
}) => {
  return (
    <div
      className={`${styles.card} ${className}`}
      data-testid="post-grid-item-skeleton"
      aria-hidden="true"
    >
      <div className={styles.imageContainer}>
        <Skeleton variant="rectangular" width="100%" height="100%" />
      </div>
      <div className={styles.content}>
        <Skeleton variant="text" width="80%" height={14} />
        <Skeleton variant="text" width="50%" height={10} className="mt-1" />
      </div>
    </div>
  );
};

const formatCount = (count: number): string => {
  if (count >= 1_000_000)
    return (count / 1_000_000).toFixed(1).replace(/\.0$/, "") + "M";
  if (count >= 1_000)
    return (count / 1_000).toFixed(1).replace(/\.0$/, "") + "k";
  return count.toString();
};

export const PostGridItem: FC<PostGridItemProps> = ({
  id,
  imageUrl,
  title,
  likesCount,
  repliesCount,
  onClick,
  className = "",
}) => {
  return (
    <div
      className={`${styles.card} ${className}`}
      onClick={() => onClick?.(id)}
      onKeyDown={
        onClick
          ? (e) => {
              if (e.key === "Enter" || e.key === " ") {
                e.preventDefault();
                onClick(id);
              }
            }
          : undefined
      }
      role={onClick ? "button" : undefined}
      tabIndex={onClick ? 0 : undefined}
      data-testid={`post-grid-item-${id}`}
    >
      <div className={styles.imageContainer}>
        <AuthImage src={imageUrl} alt={title} className={styles.image} />
      </div>
      <div className={styles.content}>
        <h4 className={styles.title} title={title}>
          {title}
        </h4>
        <p className={styles.stats}>
          {formatCount(likesCount)} likes • {formatCount(repliesCount)} replies
        </p>
      </div>
    </div>
  );
};
