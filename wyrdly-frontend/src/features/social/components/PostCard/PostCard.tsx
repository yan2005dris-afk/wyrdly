import { useState, type FC } from "react";
import { Link } from "react-router-dom";
import {
  Heart,
  Sparkles,
  PartyPopper,
  Repeat,
  MessageCircle,
  Share2,
  MoreHorizontal,
  CheckCircle,
} from "lucide-react";
import type { PostCardProps } from "./PostCard.types";
import type { ReactionType } from "../../../../types/feed";
import { Avatar } from "../../../../components/ui/Avatar";
import { AuthImage } from "../../../../components/ui/AuthImage";
import { Skeleton } from "../../../../components/ui/Skeleton";
import { CommentSection } from "../CommentSection";
import styles from "./PostCard.module.css";

export const PostCardSkeleton: FC<{ className?: string }> = ({
  className = "",
}) => {
  return (
    <article
      className={`${styles.card} ${className}`}
      data-testid="post-skeleton"
      aria-hidden="true"
    >
      <div className={styles.header}>
        <div className={styles.authorRow}>
          <Skeleton variant="circular" width={40} height={40} />
          <div className="flex flex-col gap-1.5">
            <Skeleton variant="text" width={120} height={14} />
            <Skeleton variant="text" width={80} height={10} />
          </div>
        </div>
      </div>
      <div className="flex flex-col gap-2 py-1">
        <Skeleton variant="text" width="95%" height={14} />
        <Skeleton variant="text" width="70%" height={14} />
      </div>
      <div className="flex items-center gap-6 pt-2 border-t border-slate-100">
        <Skeleton variant="rounded" width={48} height={20} />
        <Skeleton variant="rounded" width={48} height={20} />
        <Skeleton variant="rounded" width={48} height={20} />
      </div>
    </article>
  );
};

const formatCount = (count: number): string => {
  if (count >= 1_000_000) {
    return (count / 1_000_000).toFixed(1).replace(/\.0$/, "") + "M";
  }
  if (count >= 1_000) {
    return (count / 1_000).toFixed(1).replace(/\.0$/, "") + "k";
  }
  return count.toString();
};

/** Icon + active-class metadata for each reaction button. */
interface ReactionMeta {
  readonly type: ReactionType;
  readonly testId: string;
  readonly ariaLabel: string;
  readonly Icon: typeof Heart;
  readonly activeClass: string;
  readonly activeIconClass: string;
  readonly inactiveIconClass: string;
}

const REACTION_BUTTONS: readonly ReactionMeta[] = [
  {
    type: "LIKE",
    testId: "like-btn",
    ariaLabel: "Like",
    Icon: Heart,
    activeClass: styles.actionButtonLiked,
    activeIconClass: "fill-rose-500 text-rose-500",
    inactiveIconClass: "",
  },
  {
    type: "LOVE",
    testId: "love-btn",
    ariaLabel: "Love",
    Icon: Sparkles,
    activeClass: styles.actionButtonLoved,
    activeIconClass: "fill-pink-500 text-pink-500",
    inactiveIconClass: "",
  },
  {
    type: "CELEBRATE",
    testId: "celebrate-btn",
    ariaLabel: "Celebrate",
    Icon: PartyPopper,
    activeClass: styles.actionButtonCelebrated,
    activeIconClass: "fill-amber-500 text-amber-500",
    inactiveIconClass: "",
  },
];

export const PostCard: FC<PostCardProps> = ({
  post,
  onReaction,
  onBoost,
  onCommentClick,
  onShareClick,
  className = "",
  isReactionPending = false,
  isBoostPending = false,
  defaultCommentsOpen = false,
}) => {
  const isBoosted = Boolean(post.isReposted);
  const [isCommentsOpen, setIsCommentsOpen] = useState(defaultCommentsOpen);

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
          <AuthImage
            src={post.attachments[0].url}
            alt={post.attachments[0].altText || "Post attachment"}
            className={styles.mediaImage}
          />
        </div>
      )}

      {/* Post Interaction Toolbar */}
      <div className={styles.toolbar}>
        {REACTION_BUTTONS.map(
          ({ type, testId, ariaLabel, Icon, activeClass, activeIconClass }) => {
            const isActive = post.userReaction === type;
            const count = post.reactions[type] ?? 0;
            return (
              <button
                key={type}
                type="button"
                onClick={() => onReaction?.(post.id, type)}
                disabled={isReactionPending}
                aria-pressed={isActive}
                aria-busy={isReactionPending || undefined}
                aria-label={ariaLabel}
                className={`${styles.actionButton} ${isActive ? activeClass : ""}`}
                data-testid={testId}
              >
                <Icon
                  className={`w-3.5 h-3.5 ${isActive ? activeIconClass : ""}`}
                />
                <span>{formatCount(count)}</span>
              </button>
            );
          },
        )}

        <button
          type="button"
          onClick={() => onBoost?.(post.id)}
          disabled={!onBoost || isBoostPending}
          aria-pressed={isBoosted}
          aria-busy={isBoostPending || undefined}
          title={onBoost ? undefined : "Boost coming soon"}
          className={`${styles.actionButton} ${isBoosted ? styles.actionButtonBoosted : ""}`}
          data-testid="boost-btn"
          aria-label="Boost"
        >
          <Repeat className="w-3.5 h-3.5" />
          <span>{formatCount(post.repostsCount)}</span>
        </button>

        <button
          type="button"
          onClick={() => {
            setIsCommentsOpen((prev) => !prev);
            onCommentClick?.(post.id);
          }}
          className={styles.actionButton}
          data-testid="comment-btn"
          aria-label="Comments"
          aria-expanded={isCommentsOpen}
        >
          <MessageCircle className="w-3.5 h-3.5" />
          <span>{formatCount(post.commentsCount)}</span>
        </button>

        <button
          type="button"
          onClick={() => onShareClick?.(post.id)}
          className={styles.actionButton}
          data-testid="share-btn"
          aria-label="Share"
        >
          <Share2 className="w-3.5 h-3.5" />
          <span>Share</span>
        </button>
      </div>

      {isCommentsOpen && (
        <CommentSection postId={post.id} postAuthorId={post.author.id} />
      )}
    </article>
  );
};
