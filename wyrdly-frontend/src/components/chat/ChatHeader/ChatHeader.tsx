import type { FC } from "react";
import { Phone, Video, MoreVertical, CheckCircle } from "lucide-react";
import type { ChatHeaderProps } from "./ChatHeader.types";
import { Avatar } from "../../ui/Avatar";
import styles from "./ChatHeader.module.css";

export const ChatHeader: FC<ChatHeaderProps> = ({
  participant,
  isOnline = false,
  statusText = "Online",
  onCallClick,
  onVideoClick,
  onOptionsClick,
  className = "",
}) => {
  return (
    <div className={`${styles.header} ${className}`} data-testid="chat-header">
      <div className={styles.leftCol}>
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
