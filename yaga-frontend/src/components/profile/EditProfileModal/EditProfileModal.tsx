import { useState, useEffect, type FC, type FormEvent } from "react";
import { X } from "lucide-react";
import type { EditProfileModalProps } from "./EditProfileModal.types";
import { Input } from "../../ui/Input";
import { Button } from "../../ui/Button";
import styles from "./EditProfileModal.module.css";

const BIO_MAX_LENGTH = 250;

export const EditProfileModal: FC<EditProfileModalProps> = ({
  isOpen,
  profile,
  isSaving,
  onSave,
  onClose,
}) => {
  const [fullName, setFullName] = useState(profile.fullName);
  const [bio, setBio] = useState(profile.bio ?? "");
  const [avatarUrl, setAvatarUrl] = useState(profile.avatarUrl ?? "");

  useEffect(() => {
    if (isOpen) {
      setFullName(profile.fullName);
      setBio(profile.bio ?? "");
      setAvatarUrl(profile.avatarUrl ?? "");
    }
  }, [isOpen, profile]);

  if (!isOpen) return null;

  const bioLength = bio.length;
  const isBioOverLimit = bioLength > BIO_MAX_LENGTH;
  const isFormValid = fullName.trim().length > 0 && !isBioOverLimit;

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!isFormValid || isSaving) return;

    onSave({
      fullName: fullName.trim(),
      bio: bio.trim() || undefined,
      avatarUrl: avatarUrl.trim() || undefined,
    });
  };

  const handleOverlayClick = (e: React.MouseEvent<HTMLDivElement>) => {
    if (e.target === e.currentTarget && !isSaving) {
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
            disabled={isSaving}
            aria-label="Close"
            data-testid="edit-profile-close"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit}>
          <div className={styles.body}>
            <Input
              label="Full Name"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
              placeholder="Your display name"
              maxLength={100}
              required
              disabled={isSaving}
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
                disabled={isSaving}
                data-testid="edit-profile-bio"
              />
              <span className={`${styles.charCount} ${bioCountClass}`}>
                {bioLength}/{BIO_MAX_LENGTH}
              </span>
            </div>

            <Input
              label="Avatar URL"
              value={avatarUrl}
              onChange={(e) => setAvatarUrl(e.target.value)}
              placeholder="https://example.com/avatar.jpg"
              type="url"
              disabled={isSaving}
              data-testid="edit-profile-avatar"
            />
          </div>

          {/* Footer */}
          <div className={styles.footer}>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={onClose}
              disabled={isSaving}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              variant="primary"
              size="sm"
              isLoading={isSaving}
              disabled={!isFormValid || isSaving}
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
