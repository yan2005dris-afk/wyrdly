import {
  useCallback,
  useEffect,
  useState,
  type FC,
  type MouseEvent,
} from "react";
import { useQuery } from "@tanstack/react-query";
import { X, AlertCircle, FileText } from "lucide-react";
import type { PostDetailModalProps } from "./PostDetailModal.types";
import type { Post, ReactionType } from "../../../types/feed";
import { mapPostApiResponseToPost } from "../../../types/feed";
import { postsApi } from "../../../api/posts";
import { getApiErrorMessage } from "../../../api/apiErrors";
import {
  PostCard,
  PostCardSkeleton,
  useReaction,
} from "../../../features/social";
import { Button } from "../../ui/Button";
import styles from "./PostDetailModal.module.css";

export const PostDetailModal: FC<PostDetailModalProps> = ({
  postId,
  isOpen,
  onClose,
  initialPost = null,
}) => {
  const [localPostOverride, setLocalPostOverride] = useState<Post | null>(null);

  const isInitialValid = Boolean(initialPost && initialPost.id === postId);

  const {
    data: fetchedPost,
    isLoading: isQueryLoading,
    error: queryError,
    refetch,
  } = useQuery({
    queryKey: ["post", postId],
    queryFn: async () => {
      const response = await postsApi.getById(postId!);
      return mapPostApiResponseToPost(response);
    },
    enabled: Boolean(isOpen && postId && !isInitialValid),
  });

  const { react: reactToPost, isPending: isReactionPending } = useReaction();

  const handleClose = useCallback(() => {
    setLocalPostOverride(null);
    onClose();
  }, [onClose]);

  // Lock body scroll while modal is active
  useEffect(() => {
    if (!isOpen) return;
    const originalOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = originalOverflow;
    };
  }, [isOpen]);

  // Handle Escape key
  useEffect(() => {
    if (!isOpen) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        handleClose();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isOpen, handleClose]);

  if (!isOpen || !postId) return null;

  const basePost = isInitialValid ? initialPost : (fetchedPost ?? null);
  const activePost = localPostOverride ?? basePost;
  const isLoading = !isInitialValid && isQueryLoading;
  const error = queryError ? getApiErrorMessage(queryError) : null;

  const handleOverlayClick = (e: MouseEvent<HTMLDivElement>) => {
    if (e.target === e.currentTarget) {
      handleClose();
    }
  };

  const handleReaction = (targetPostId: string, reaction: ReactionType) => {
    if (!activePost || activePost.id !== targetPostId) return;

    const currentActive = activePost.userReaction === reaction;
    const diff = currentActive ? -1 : 1;
    const snapshot = activePost;
    const optimistic: Post = {
      ...activePost,
      userReaction: currentActive ? undefined : reaction,
      reactions: {
        ...activePost.reactions,
        [reaction]: Math.max(0, (activePost.reactions[reaction] ?? 0) + diff),
      },
    };
    setLocalPostOverride(optimistic);

    void reactToPost(targetPostId, reaction, {
      onRollback: () => {
        setLocalPostOverride(snapshot);
      },
    });
  };

  return (
    <div
      className={styles.overlay}
      onClick={handleOverlayClick}
      data-testid="post-detail-modal-overlay"
      role="presentation"
    >
      <div
        className={styles.modal}
        role="dialog"
        aria-modal="true"
        aria-labelledby="post-detail-modal-title"
        data-testid="post-detail-modal"
      >
        {/* Header */}
        <div className={styles.header}>
          <div className={styles.headerLeft}>
            <FileText className="w-5 h-5 text-indigo-600" />
            <h2 id="post-detail-modal-title" className={styles.title}>
              Publication
            </h2>
          </div>
          <button
            type="button"
            className={styles.closeButton}
            onClick={handleClose}
            aria-label="Close modal"
            data-testid="post-detail-modal-close"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content Body */}
        <div className={styles.body}>
          {isLoading && (
            <div
              className={styles.loadingContainer}
              data-testid="post-modal-loading"
            >
              <PostCardSkeleton />
            </div>
          )}

          {!isLoading && error && (
            <div
              className={styles.errorContainer}
              data-testid="post-modal-error"
            >
              <AlertCircle className="w-12 h-12 text-rose-500 mb-1" />
              <h3 className={styles.errorTitle}>Post unavailable</h3>
              <p className={styles.errorMessage}>{error}</p>
              <div className="flex items-center gap-3 mt-4">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => void refetch()}
                  data-testid="post-modal-retry"
                >
                  Retry
                </Button>
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={handleClose}
                  data-testid="post-modal-close-error"
                >
                  Close
                </Button>
              </div>
            </div>
          )}

          {!isLoading && !error && activePost && (
            <div data-testid="post-modal-content">
              <PostCard
                post={activePost}
                onReaction={handleReaction}
                isReactionPending={isReactionPending(activePost.id)}
                defaultCommentsOpen={true}
              />
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
