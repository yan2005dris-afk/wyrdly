import { useState, useRef, useEffect, type FC, type FormEvent } from "react";
import { X } from "lucide-react";
import type { EditProfileModalProps } from "./EditProfileModal.types";
import { Input } from "../../../../components/ui/Input";
import { Button } from "../../../../components/ui/Button";
import { useMediaUpload } from "../../../../hooks/useMediaUpload";
import { AvatarUploadField } from "./AvatarUploadField";
import styles from "./EditProfileModal.module.css";

const BIO_MAX_LENGTH = 250;

// react-doctor-disable-next-line react-doctor/no-high-complexity-react-function
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
  const [uploadSuccess, setUploadSuccess] = useState<string | null>(null);
  const [isLocalUploading, setIsLocalUploading] = useState(false);

  const objectUrlRef = useRef<string | null>(null);

  const mediaUpload = useMediaUpload();

  useEffect(() => {
    return () => {
      if (previewUrl) {
        URL.revokeObjectURL(previewUrl);
      }
    };
  }, [previewUrl]);

  if (!isOpen) return null;

  const isUploading = isLocalUploading || mediaUpload.isUploading;
  const currentUploadError = localError || mediaUpload.error;

  const bioLength = bio.length;
  const isBioOverLimit = bioLength > BIO_MAX_LENGTH;
  const isFormValid =
    fullName.trim().length > 0 && !isBioOverLimit && !isUploading;

  const handleFileSelect = async (file: File) => {
    setLocalError(null);
    setUploadSuccess(null);

    // Instant local preview
    if (objectUrlRef.current) {
      URL.revokeObjectURL(objectUrlRef.current);
    }
    // react-doctor-disable-next-line react-doctor/no-create-object-url-without-revoke
    const localUrl = URL.createObjectURL(file);
    objectUrlRef.current = localUrl;
    setPreviewUrl(localUrl);

    if (uploadAvatar) {
      setIsLocalUploading(true);
      try {
        const url = await uploadAvatar(file);
        if (url) {
          setAvatarUrl(url);
          setUploadSuccess("Photo uploaded successfully!");
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
        setUploadSuccess("Photo uploaded successfully!");
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
    // react-doctor-disable-next-line react-doctor/no-static-element-interactions
    <div
      className={styles.overlay}
      onClick={handleOverlayClick}
      data-testid="edit-profile-overlay"
    >
      {/* react-doctor-disable-next-line react-doctor/prefer-html-dialog */}
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
            <AvatarUploadField
              currentUrl={avatarUrl}
              previewUrl={previewUrl}
              alt={fullName.trim() || profile.fullName}
              isUploading={isUploading}
              isDisabled={isSaving}
              error={currentUploadError}
              successMessage={uploadSuccess}
              onFileSelect={handleFileSelect}
            />

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
