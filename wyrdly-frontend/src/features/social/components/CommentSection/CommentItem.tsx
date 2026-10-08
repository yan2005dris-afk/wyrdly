import type { FC } from "react";
import { Link } from "react-router-dom";
import { Trash } from "lucide-react";
import { Avatar } from "../../../../components/ui/Avatar";
import type { Comment } from "../../../../types/comments";
import styles from "./CommentItem.module.css";

export interface CommentItemProps {
  readonly comment: Comment;
  readonly currentUserId: string;
  readonly postAuthorId: string;
  readonly onDelete: (commentId: string) => void;
}

export const CommentItem: FC<CommentItemProps> = ({
  comment,
  currentUserId,
  postAuthorId,
  onDelete,
}) => {
  const canDelete =
    currentUserId === comment.authorId || currentUserId === postAuthorId;

  const authorProfileUrl = `/profile/${comment.author.username}`;

  return (
    <article className={styles.item} data-testid={`comment-${comment.id}`}>
      <Link
        to={authorProfileUrl}
        aria-label={`Perfil de ${comment.author.fullName}`}
      >
        <Avatar
          src={comment.author.avatarUrl}
          alt={comment.author.fullName}
          size="sm"
        />
      </Link>
      <div className={styles.body}>
        <header className={styles.header}>
          <Link to={authorProfileUrl} className={styles.authorName}>
            {comment.author.fullName}
          </Link>
          <span className={styles.authorHandle}>
            @{comment.author.username}
          </span>
          <span className={styles.timestamp}>• {comment.createdAt}</span>
        </header>
        <p
          className={styles.content}
          data-testid={`comment-content-${comment.id}`}
        >
          {comment.content}
        </p>
      </div>
      {canDelete && (
        <button
          type="button"
          className={styles.delete}
          aria-label="Eliminar comentario"
          onClick={() => onDelete(comment.id)}
          data-testid={`comment-delete-${comment.id}`}
        >
          <Trash className="w-3.5 h-3.5" aria-hidden="true" />
        </button>
      )}
    </article>
  );
};
