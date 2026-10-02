import { useRef, type FC, type ChangeEvent, type KeyboardEvent } from "react";
import { Camera, Loader2 } from "lucide-react";
import { Avatar } from "../../../../components/ui/Avatar";
import { ALLOWED_MIME_TYPES } from "../../../../types/media";
import styles from "./EditProfileModal.module.css";

interface AvatarUploadFieldProps {
  currentUrl?: string;
  previewUrl: string | null;
  alt: string;
  isUploading: boolean;
  isDisabled: boolean;
  error: string | null;
  onFileSelect: (file: File) => void;
}

export const AvatarUploadField: FC<AvatarUploadFieldProps> = ({
  currentUrl,
  previewUrl,
  alt,
  isUploading,
  isDisabled,
  error,
  onFileSelect,
}) => {
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleClick = () => {
    if (isDisabled || isUploading) return;
    fileInputRef.current?.click();
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      handleClick();
    }
  };

  const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      onFileSelect(file);
    }
  };

  return (
    <div className={styles.avatarSection}>
      <div
        className={styles.avatarTrigger}
        onClick={handleClick}
        onKeyDown={handleKeyDown}
        role="button"
        tabIndex={isDisabled || isUploading ? -1 : 0}
        aria-label="Change profile picture"
        data-testid="edit-profile-avatar-trigger"
      >
        <Avatar
          src={previewUrl || currentUrl || undefined}
          alt={alt}
          size="xl"
        />
        <div
          className={`${styles.avatarOverlay} ${isUploading ? styles.avatarOverlayVisible : ""}`}
          aria-hidden="true"
        >
          {isUploading ? (
            <Loader2 className={styles.spinner} />
          ) : (
            <Camera className={styles.cameraIcon} />
          )}
          <span className={styles.overlayText}>
            {isUploading ? "Uploading…" : "Change"}
          </span>
        </div>
      </div>

      <input
        ref={fileInputRef}
        type="file"
        accept={ALLOWED_MIME_TYPES.join(",")}
        onChange={handleChange}
        className={styles.hiddenFileInput}
        data-testid="edit-profile-avatar-input"
        disabled={isDisabled || isUploading}
        aria-label="Upload profile picture"
      />

      <span className={styles.avatarHint}>
        Click to change photo (JPEG, PNG, GIF, WebP)
      </span>

      {error && (
        <span
          className={styles.avatarError}
          role="alert"
          data-testid="edit-profile-avatar-error"
        >
          {error}
        </span>
      )}
    </div>
  );
};
