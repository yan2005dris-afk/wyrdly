import {
  useState,
  useRef,
  useEffect,
  type FC,
  type FormEvent,
  type ChangeEvent,
  type KeyboardEvent,
} from "react";
import { X, Camera, Loader2 } from "lucide-react";
import type { EditProfileModalProps } from "./EditProfileModal.types";
import { Input } from "../../ui/Input";
import { Button } from "../../ui/Button";
import { Avatar } from "../../ui/Avatar";
import { useMediaUpload } from "../../../hooks/useMediaUpload";
import { ALLOWED_MIME_TYPES } from "../../../types/media";
import styles from "./EditProfileModal.module.css";

const BIO_MAX_LENGTH = 250;

export const EditProfileModal: FC<EditProfileModalProps> = ({
  isOpen,
  profile,
  isSaving,
  onSave,
  onClose,
  uploadAvatar,
}) => {
  const [fullName, setFullName] = useState(profile.fullName);
  const [bio, setBio] = useState(profile.bio ?? "");
  const [avatarUrl, setAvatarUrl] = useState(profile.avatarUrl ?? "");
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [localError, setLocalError] = useState<string | null>(null);
  const [isLocalUploading, setIsLocalUploading] = useState(false);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const objectUrlRef = useRef<string | null>(null);

  const mediaUpload = useMediaUpload();

  useEffect(() => {
    return () => {
      if (objectUrlRef.current) {
        URL.revokeObjectURL(objectUrlRef.current);
      }
    };
  }, []);

  if (!isOpen) return null;

  const isUploading = isLocalUploading || mediaUpload.isUploading;
  const currentUploadError = localError || mediaUpload.error;

  const bioLength = bio.length;
  const isBioOverLimit = bioLength > BIO_MAX_LENGTH;
  const isFormValid =
    fullName.trim().length > 0 && !isBioOverLimit && !isUploading;

  const handleAvatarClick = () => {
    if (isSaving || isUploading) return;
    fileInputRef.current?.click();
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      handleAvatarClick();
    }
  };

  const handleFileChange = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setLocalError(null);

    // Instant local preview
    if (objectUrlRef.current) {
      URL.revokeObjectURL(objectUrlRef.current);
    }
    const localUrl = URL.createObjectURL(file);
    objectUrlRef.current = localUrl;
    setPreviewUrl(localUrl);

    if (uploadAvatar) {
      setIsLocalUploading(true);
      try {
        const url = await uploadAvatar(file);
        if (url) {
          setAvatarUrl(url);
        } else {
          setLocalError("Failed to upload avatar. Please try again.");
        }
      } catch (err) {
        const msg =
          err instanceof Error ? err.message : "Failed to upload avatar";
        setLocalError(msg);
      } finally {
        setIsLocalUploading(false);
      }
    } else {
      const res = await mediaUpload.upload(file);
      if (res?.fileUrl) {
        setAvatarUrl(res.fileUrl);
      }
    }
  };

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!isFormValid || isSaving || isUploading) return;

    onSave({
      fullName: fullName.trim(),
      bio: bio.trim() || undefined,
      avatarUrl: avatarUrl.trim() || undefined,
    });
  };

  const handleOverlayClick = (e: React.MouseEvent<HTMLDivElement>) => {
    if (e.target === e.currentTarget && !isSaving && !isUploading) {
      onClose();
    }
  };

  const bioCountClass = isBioOverLimit
    ? styles.charCountError
    : bioLength > BIO_MAX_LENGTH * 0.9
      ? styles.charCountWarn
      : "";

  return (
    <div
      className={styles.overlay}
      onClick={handleOverlayClick}
      data-testid="edit-profile-overlay"
    >
      <div className={styles.modal} role="dialog" aria-label="Edit profile">
        {/* Header */}
        <div className={styles.header}>
          <h2 className={styles.title}>Edit Profile</h2>
          <button
            className={styles.closeButton}
            onClick={onClose}
            disabled={isSaving || isUploading}
            aria-label="Close"
            data-testid="edit-profile-close"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit}>
          <div className={styles.body}>
            {/* Click-to-Upload Avatar */}
            <div className={styles.avatarSection}>
              <div
                className={styles.avatarTrigger}
                onClick={handleAvatarClick}
                onKeyDown={handleKeyDown}
                role="button"
                tabIndex={isSaving || isUploading ? -1 : 0}
                aria-label="Change profile picture"
                data-testid="edit-profile-avatar-trigger"
              >
                <Avatar
                  src={previewUrl || avatarUrl || undefined}
                  alt={fullName.trim() || profile.fullName}
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
                onChange={handleFileChange}
                className={styles.hiddenFileInput}
                data-testid="edit-profile-avatar-input"
                disabled={isSaving || isUploading}
                aria-label="Upload profile picture"
              />

              <span className={styles.avatarHint}>
                Click to change photo (JPEG, PNG, GIF, WebP)
              </span>

              {currentUploadError && (
                <span
                  className={styles.avatarError}
                  role="alert"
                  data-testid="edit-profile-avatar-error"
                >
                  {currentUploadError}
                </span>
              )}
            </div>

            <Input
              label="Full Name"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              placeholder="Your display name"
              maxLength={100}
              required
              disabled={isSaving || isUploading}
              data-testid="edit-profile-fullname"
            />

            <div className={styles.textareaContainer}>
              <label className={styles.label} htmlFor="edit-bio">
                Bio
              </label>
              <textarea
                id="edit-bio"
                className={styles.textarea}
                value={bio}
                onChange={(e) => setBio(e.target.value)}
                placeholder="Tell people about yourself"
                disabled={isSaving || isUploading}
                data-testid="edit-profile-bio"
              />
              <span className={`${styles.charCount} ${bioCountClass}`}>
                {bioLength}/{BIO_MAX_LENGTH}
              </span>
            </div>
          </div>

          {/* Footer */}
          <div className={styles.footer}>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={onClose}
              disabled={isSaving || isUploading}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              variant="primary"
              size="sm"
              isLoading={isSaving}
              disabled={!isFormValid || isSaving || isUploading}
              data-testid="edit-profile-save"
            >
              Save Changes
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};
