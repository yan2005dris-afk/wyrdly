import type { FC } from "react";
import { useComments } from "../../hooks/useComments";
import { Skeleton } from "../../../../components/ui/Skeleton";
import { CommentItem } from "./CommentItem";
import styles from "./CommentList.module.css";

export interface CommentListProps {
  readonly postId: string;
  readonly currentUserId: string;
  readonly postAuthorId: string;
}

const SKELETON_KEYS: readonly string[] = [
  "skeleton-1",
  "skeleton-2",
  "skeleton-3",
];

export const CommentList: FC<CommentListProps> = ({
  postId,
  currentUserId,
  postAuthorId,
}) => {
  const { comments, isLoading, deleteComment, isDeleting } =
    useComments(postId);

  const handleDelete = async (commentId: string): Promise<void> => {
    if (isDeleting) return;
    await deleteComment(commentId);
  };

  if (isLoading) {
    return (
      <ul
        className={styles.list}
        data-testid="comment-list-skeleton"
        aria-busy="true"
      >
        {SKELETON_KEYS.map((key) => (
          <li key={key} className={styles.skeletonItem}>
            <Skeleton variant="circular" width={32} height={32} />
            <div
              style={{
                flex: 1,
                display: "flex",
                flexDirection: "column",
                gap: 6,
              }}
            >
              <Skeleton variant="text" width="40%" height={10} />
              <Skeleton variant="text" width="90%" height={10} />
            </div>
          </li>
        ))}
      </ul>
    );
  }

  if (comments.length === 0) {
    return (
      <p className={styles.empty} data-testid="comment-list-empty">
        Sé el primero en comentar.
      </p>
    );
  }

  return (
    <ul
      className={styles.list}
      data-testid="comment-list"
      aria-label="Comentarios"
    >
      {comments.map((comment) => (
        <li key={comment.id}>
          <CommentItem
            comment={comment}
            currentUserId={currentUserId}
            postAuthorId={postAuthorId}
            onDelete={(commentId) => {
              void handleDelete(commentId);
            }}
          />
        </li>
      ))}
    </ul>
  );
};
