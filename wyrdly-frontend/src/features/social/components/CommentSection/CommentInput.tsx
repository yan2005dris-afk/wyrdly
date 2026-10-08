import { useState, type ChangeEvent, type FC, type FormEvent } from "react";
import { Send } from "lucide-react";
import { useComments } from "../../hooks/useComments";
import type { Comment } from "../../../../types/comments";
import styles from "./CommentInput.module.css";

export interface CommentInputProps {
  readonly postId: string;
  readonly onCreated?: (comment: Comment) => void;
}

const MAX_LENGTH = 1000;

export const CommentInput: FC<CommentInputProps> = ({ postId, onCreated }) => {
  const [content, setContent] = useState<string>("");
  const { createComment, isCreating } = useComments(postId);

  const trimmedLength = content.trim().length;
  const isOverLimit = content.length > MAX_LENGTH;
  const isEmpty = trimmedLength === 0;
  const isDisabled = isEmpty || isOverLimit || isCreating;

  const handleChange = (e: ChangeEvent<HTMLTextAreaElement>): void => {
    setContent(e.target.value);
  };

  const handleSubmit = async (e: FormEvent<HTMLFormElement>): Promise<void> => {
    e.preventDefault();
    if (isDisabled) return;
    const trimmed = content.trim();
    try {
      const created = await createComment(trimmed);
      setContent("");
      onCreated?.(created);
    } catch {
      // Error state is exposed via useComments.isError / query.error
      // for the caller to surface. Keep the text so the user can retry.
    }
  };

  return (
    <form
      className={styles.form}
      onSubmit={(e) => {
        void handleSubmit(e);
      }}
      data-testid="comment-input-form"
    >
      <textarea
        className={styles.textarea}
        value={content}
        onChange={handleChange}
        placeholder="Escribe un comentario..."
        disabled={isCreating}
        rows={2}
        aria-label="Nuevo comentario"
        data-testid="comment-textarea"
      />
      <div className={styles.footer}>
        <span
          className={`${styles.counter} ${isOverLimit ? styles.counterOver : ""}`}
          data-testid="comment-counter"
        >
          {content.length}/{MAX_LENGTH}
        </span>
        <button
          type="submit"
          className={styles.submit}
          disabled={isDisabled}
          aria-label="Publicar comentario"
          data-testid="comment-submit"
        >
          <Send className="w-3 h-3" aria-hidden="true" />
          Comentar
        </button>
      </div>
    </form>
  );
};
