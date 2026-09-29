import type { FC } from "react";
import type { PostGridItemProps } from "./PostGridItem.types";
import styles from "./PostGridItem.module.css";

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
      role={onClick ? "button" : undefined}
      tabIndex={onClick ? 0 : undefined}
      data-testid={`post-grid-item-${id}`}
    >
      <div className={styles.imageContainer}>
        <img
          src={imageUrl}
          alt={title}
          className={styles.image}
          loading="lazy"
        />
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
