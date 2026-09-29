import { useCallback, useState } from "react";
import { mediaApi } from "../api/media";
import {
  isAllowedMimeType,
  MAX_FILE_SIZE_BYTES,
  type MediaUploadResponse,
} from "../types/media";

interface UseMediaUploadReturn {
  readonly upload: (file: File) => Promise<MediaUploadResponse | null>;
  readonly isUploading: boolean;
  readonly error: string | null;
}

const SIZE_LIMIT_MESSAGE = "File exceeds 10MB limit";
const UNSUPPORTED_TYPE_MESSAGE =
  "Unsupported file type. Use JPEG, PNG, WebP, or MP4";

export function useMediaUpload(): UseMediaUploadReturn {
  const [isUploading, setIsUploading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const upload = useCallback(
    async (file: File): Promise<MediaUploadResponse | null> => {
      setError(null);

      if (file.size > MAX_FILE_SIZE_BYTES) {
        setError(SIZE_LIMIT_MESSAGE);
        return null;
      }

      if (!isAllowedMimeType(file.type)) {
        setError(UNSUPPORTED_TYPE_MESSAGE);
        return null;
      }

      setIsUploading(true);
      try {
        const response = await mediaApi.upload(file);
        return response;
      } catch (err) {
        const message =
          err instanceof Error ? err.message : "Failed to upload file";
        setError(message);
        return null;
      } finally {
        setIsUploading(false);
      }
    },
    [],
  );

  return { upload, isUploading, error };
}
