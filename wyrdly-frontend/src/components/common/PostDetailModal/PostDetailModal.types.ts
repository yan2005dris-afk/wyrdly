import type { Post } from "../../../types/feed";

export interface PostDetailModalProps {
  readonly postId: string | null;
  readonly isOpen: boolean;
  readonly onClose: () => void;
  readonly initialPost?: Post | null;
}
