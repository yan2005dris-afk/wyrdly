import type { FC } from "react";
import {
  ArrowLeft,
  Phone,
  Video,
  MoreVertical,
  CheckCircle,
} from "lucide-react";
import type { ChatHeaderProps } from "./ChatHeader.types";
import { Avatar } from "../../../../components/ui/Avatar";
import styles from "./ChatHeader.module.css";

export const ChatHeader: FC<ChatHeaderProps> = ({
  participant,
  isOnline = false,
  statusText = "Online",
  onBack,
  onCallClick,
  onVideoClick,
  onOptionsClick,
  className = "",
}) => {
  return (
    <div className={`${styles.header} ${className}`} data-testid="chat-header">
      <div className={styles.leftCol}>
        {onBack && (
          <button
            type="button"
            onClick={onBack}
            className={styles.backBtn}
            aria-label="Back to conversations"
            data-testid="chat-back-btn"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>
        )}
        <Avatar
          src={participant.avatarUrl}
          alt={participant.fullName}
          size="md"
          isOnline={isOnline}
        />
        <div>
          <div className={styles.nameRow}>
            <h3 className={styles.contactName}>{participant.fullName}</h3>
            {participant.isVerified && (
              <span
                className={styles.verifiedIcon}
                data-testid="chat-verified-badge"
              >
                <CheckCircle className="w-3.5 h-3.5 fill-indigo-600 text-white" />
              </span>
            )}
          </div>
          <p className={styles.statusText} data-testid="chat-header-status">
            {statusText}
          </p>
        </div>
      </div>

      <div className={styles.actionsGroup}>
        {onCallClick && (
          <button
            type="button"
            onClick={onCallClick}
            className={styles.actionBtn}
            aria-label="Audio call"
            data-testid="chat-call-btn"
          >
            <Phone className="w-4 h-4" />
          </button>
        )}
        {onVideoClick && (
          <button
            type="button"
            onClick={onVideoClick}
            className={styles.actionBtn}
            aria-label="Video call"
            data-testid="chat-video-btn"
          >
            <Video className="w-4 h-4" />
          </button>
        )}
        {onOptionsClick && (
          <button
            type="button"
            onClick={onOptionsClick}
            className={styles.actionBtn}
            aria-label="More options"
            data-testid="chat-options-btn"
          >
            <MoreVertical className="w-4 h-4" />
          </button>
        )}
      </div>
    </div>
  );
};
