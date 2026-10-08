export interface BottomNavProps {
  /** Total unread chat messages for badge display */
  readonly unreadMessagesCount?: number;
  /** Optional handler when clicking the central 'New Post' button */
  readonly onNewPostClick?: () => void;
  /** Current user's username to direct to their profile */
  readonly profileUsername?: string;
  /** Custom classes */
  readonly className?: string;
}
