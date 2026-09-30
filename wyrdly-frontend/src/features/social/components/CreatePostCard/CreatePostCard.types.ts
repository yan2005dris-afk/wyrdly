import type { UserProfileSummary } from "../../../../types/domain";
import type { PostVisibility } from "../../../../types/feed";

export interface CreatePostCardProps {
  readonly currentUser?: UserProfileSummary;
  readonly isSubmitting?: boolean;
  readonly uploadMedia?: (file: File) => Promise<string | null>;
  readonly isUploadingMedia?: boolean;
  readonly onPublish: (payload: {
    readonly content: string;
    readonly visibility: PostVisibility;
    readonly attachments?: readonly File[];
    readonly mediaUrl?: string;
  }) => Promise<void> | void;
  readonly className?: string;
}
