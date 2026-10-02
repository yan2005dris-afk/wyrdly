import {
  useState,
  useRef,
  type FC,
  type ChangeEvent,
  type FormEvent,
} from "react";
import { Image, BarChart2, Globe, X } from "lucide-react";
import type { CreatePostCardProps } from "./CreatePostCard.types";
import type { PostVisibility } from "../../../../types/feed";
import { Avatar } from "../../../../components/ui/Avatar";
import { Button } from "../../../../components/ui/Button";
import styles from "./CreatePostCard.module.css";

export const CreatePostCard: FC<CreatePostCardProps> = ({
  currentUser,
  isSubmitting = false,
  uploadMedia,
  isUploadingMedia = false,
  onPublish,
  className = "",
}) => {
  const [content, setContent] = useState("");
  const [visibility, setVisibility] = useState<PostVisibility>("PUBLIC");
  const [selectedFiles, setSelectedFiles] = useState<readonly File[]>([]);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleFileChange = (e: ChangeEvent<HTMLInputElement>) => {
    if (e.target.files) {
      const filesArray = Array.from(e.target.files);
      setSelectedFiles((prev) => [...prev, ...filesArray]);
      setUploadError(null);
    }
  };

  const removeFile = (index: number) => {
    setSelectedFiles((prev) => prev.filter((_, i) => i !== index));
    setUploadError(null);
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!content.trim() && selectedFiles.length === 0) return;

    let mediaUrl: string | undefined;

    if (uploadMedia && selectedFiles.length > 0) {
      const url = await uploadMedia(selectedFiles[0]);
      if (!url) {
        setUploadError("Upload failed. Please try again.");
        return;
      }
      mediaUrl = url;
    }

    await onPublish({
      content: content.trim(),
      visibility,
      attachments: selectedFiles.length > 0 ? selectedFiles : undefined,
      mediaUrl,
    });

    setContent("");
    setSelectedFiles([]);
    setUploadError(null);
  };

  const toggleVisibility = () => {
    const next: Record<PostVisibility, PostVisibility> = {
      PUBLIC: "FEDERATED",
      FEDERATED: "FOLLOWERS",
      FOLLOWERS: "PUBLIC",
    };
    setVisibility((prev) => next[prev]);
  };

  return (
    <div
      className={`${styles.card} ${className}`}
      data-testid="create-post-card"
    >
      <form onSubmit={handleSubmit}>
        <div className={styles.topRow}>
          <Avatar
            src={currentUser?.avatarUrl}
            alt={currentUser?.fullName || "User"}
            size="md"
          />
          <div className={styles.textareaWrapper}>
            <textarea
              rows={2}
              placeholder="Share an update with your federated graph..."
              aria-label="Share an update with your federated graph"
              value={content}
              onChange={(e) => setContent(e.target.value)}
              className={styles.textarea}
              data-testid="create-post-textarea"
            />

            {selectedFiles.length > 0 && (
              <div className={styles.attachmentPreviews}>
                {selectedFiles.map((file, idx) => (
                  <span
                    key={`${file.name}-${file.size}-${file.lastModified}`}
                    className={styles.previewPill}
                  >
                    <span>{file.name}</span>
                    <button
                      type="button"
                      onClick={() => removeFile(idx)}
                      className={styles.removeAttachmentBtn}
                      aria-label={`Remove ${file.name}`}
                    >
                      <X className="w-3 h-3" />
                    </button>
                  </span>
                ))}
              </div>
            )}
          </div>
        </div>

        {uploadError && (
          <div
            data-testid="upload-error"
            role="alert"
            style={{
              marginTop: "0.5rem",
              fontSize: "0.75rem",
              color: "#b91c1c",
            }}
          >
            {uploadError}
          </div>
        )}

        <div className={styles.bottomToolbar}>
          <div className={styles.toolsGroup}>
            <input
              type="file"
              ref={fileInputRef}
              onChange={handleFileChange}
              multiple
              accept="image/*,video/*"
              className={styles.hiddenFileInput}
              data-testid="file-upload-input"
              aria-label="Upload media files"
            />
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              className={`${styles.toolButton} ${styles.toolButtonMedia}`}
              data-testid="add-media-btn"
            >
              <Image className="w-3.5 h-3.5" />
              <span>Media</span>
              <span className={styles.storageBadge}>S3 • RustFS</span>
            </button>

            <button
              type="button"
              className={styles.toolButton}
              title="Polls coming soon"
            >
              <BarChart2 className="w-3.5 h-3.5" />
              <span>Poll</span>
            </button>

            <button
              type="button"
              onClick={toggleVisibility}
              className={styles.toolButton}
              data-testid="visibility-toggle-btn"
            >
              <Globe className="w-3.5 h-3.5" />
              <span>{visibility.toLowerCase()}</span>
            </button>
          </div>

          <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
            {isUploadingMedia && (
              <span
                data-testid="uploading-indicator"
                style={{ fontSize: "0.75rem", color: "#64748b" }}
              >
                Uploading...
              </span>
            )}
            <Button
              type="submit"
              variant="primary"
              size="sm"
              isLoading={isSubmitting || isUploadingMedia}
              disabled={
                isUploadingMedia ||
                (!content.trim() && selectedFiles.length === 0)
              }
              data-testid="publish-post-btn"
            >
              Publish
            </Button>
          </div>
        </div>
      </form>
    </div>
  );
};
