import type { FC } from "react";
import { useAuth } from "../../../auth/hooks/useAuth";
import { CommentInput } from "./CommentInput";
import { CommentList } from "./CommentList";
import styles from "./CommentSection.module.css";

export interface CommentSectionProps {
  readonly postId: string;
  readonly postAuthorId: string;
}

export const CommentSection: FC<CommentSectionProps> = ({
  postId,
  postAuthorId,
}) => {
  const { user } = useAuth();
  const currentUserId = user?.id ?? "";

  return (
    <section
      className={styles.section}
      data-testid={`comment-section-${postId}`}
    >
      <CommentInput postId={postId} />
      <CommentList
        postId={postId}
        currentUserId={currentUserId}
        postAuthorId={postAuthorId}
      />
    </section>
  );
};
