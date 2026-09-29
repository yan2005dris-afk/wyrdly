import type { FC } from "react";
import { Link } from "react-router-dom";
import {
  Heart,
  Repeat,
  MessageCircle,
  Share2,
  MoreHorizontal,
  CheckCircle,
} from "lucide-react";
import type { PostCardProps } from "./PostCard.types";
import { Avatar } from "../../ui/Avatar";
import styles from "./PostCard.module.css";

const formatCount = (count: number): string => {
  if (count >= 1_000_000) {
    return (count / 1_000_000).toFixed(1).replace(/\.0$/, "") + "M";
  }
  if (count >= 1_000) {
    return (count / 1_000).toFixed(1).replace(/\.0$/, "") + "k";
  }
  return count.toString();
};

export const PostCard: FC<PostCardProps> = ({
  post,
  onReaction,
  onCommentClick,
  onShareClick,
  className = "",
}) => {
  const isLiked = post.userReaction === "LIKE" || post.userReaction === "LOVE";
  const isBoosted = post.userReaction === "RETWEET";

  const authorProfileUrl = `/profile/${post.author.username}`;

  return (
    <article
      className={`${styles.card} ${className}`}
      data-testid={`post-card-${post.id}`}
    >
      {/* Post Header */}
      <div className={styles.header}>
        <div className={styles.authorRow}>
          <Link to={authorProfileUrl}>
            <Avatar
              src={post.author.avatarUrl}
              alt={post.author.fullName}
              size="md"
            />
          </Link>
          <div>
            <div className={styles.authorMeta}>
              <Link to={authorProfileUrl} className={styles.authorName}>
                {post.author.fullName}
              </Link>
              {post.author.isVerified && (
                <span
                  className={styles.verifiedIcon}
                  data-testid="post-author-verified"
                >
                  <CheckCircle className="w-3 h-3 fill-indigo-600 text-white" />
                </span>
              )}
              <span className={styles.timestamp}>• {post.createdAt}</span>
            </div>
            <p className={styles.authorHandle}>
              @{post.author.username} • {post.author.instanceUrl}
            </p>
          </div>
        </div>

        <button
          type="button"
          className={styles.kebabButton}
          aria-label="Post options"
        >
          <MoreHorizontal className="w-4 h-4" />
        </button>
      </div>

      {/* Post Content */}
      <p className={styles.content} data-testid="post-content">
        {post.content}
      </p>

      {/* Post Attachments (RustFS / S3) */}
      {post.attachments && post.attachments.length > 0 && (
        <div
          className={styles.mediaContainer}
          data-testid="post-media-container"
        >
          <img
            src={post.attachments[0].url}
            alt={post.attachments[0].altText || "Post attachment"}
            className={styles.mediaImage}
            loading="lazy"
          />
          <span className={styles.rustfsBadge} data-testid="rustfs-badge">
            RustFS S3 Bucket
          </span>
        </div>
      )}

      {/* Post Interaction Toolbar */}
      <div className={styles.toolbar}>
        <button
          type="button"
          onClick={() => onReaction?.(post.id, "LIKE")}
          className={`${styles.actionButton} ${isLiked ? styles.actionButtonLiked : ""}`}
          data-testid="like-btn"
        >
          <Heart
            className={`w-3.5 h-3.5 ${isLiked ? "fill-rose-500 text-rose-500" : ""}`}
          />
          <span>{formatCount(post.reactions.LIKE)}</span>
        </button>

        <button
          type="button"
          onClick={() => onReaction?.(post.id, "RETWEET")}
          className={`${styles.actionButton} ${isBoosted ? styles.actionButtonBoosted : ""}`}
          data-testid="boost-btn"
        >
          <Repeat className="w-3.5 h-3.5" />
          <span>{formatCount(post.reactions.RETWEET)}</span>
        </button>

        <button
          type="button"
          onClick={() => onCommentClick?.(post.id)}
          className={styles.actionButton}
          data-testid="comment-btn"
        >
          <MessageCircle className="w-3.5 h-3.5" />
          <span>{formatCount(post.commentsCount)}</span>
        </button>

        <button
          type="button"
          onClick={() => onShareClick?.(post.id)}
          className={styles.actionButton}
          data-testid="share-btn"
        >
          <Share2 className="w-3.5 h-3.5" />
          <span>Share</span>
        </button>
      </div>
    </article>
  );
};
