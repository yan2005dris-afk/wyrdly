import { useCallback, useState } from "react";
import { mediaApi } from "../api/media";
import {
  isAllowedMimeType,
  MAX_FILE_SIZE_BYTES,
  type MediaUploadResponse,
} from "../types/media";
import {
  convertToWebP,
  isRasterInputMimeType,
} from "../utils/imageTransform";

interface UseMediaUploadReturn {
  readonly upload: (file: File) => Promise<MediaUploadResponse | null>;
  readonly isUploading: boolean;
  readonly error: string | null;
}

const SIZE_LIMIT_MESSAGE = "File exceeds 10MB limit";
const UNSUPPORTED_TYPE_MESSAGE =
  "Unsupported file type. Use JPEG, PNG, GIF, BMP, or WebP";
const CONVERSION_FAILED_MESSAGE =
  "Could not convert image to WebP. Try a different file.";

export function useMediaUpload(): UseMediaUploadReturn {
  const [isUploading, setIsUploading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const upload = useCallback(
    async (file: File): Promise<MediaUploadResponse | null> => {
      setError(null);

      if (!isAllowedMimeType(file.type)) {
        setError(UNSUPPORTED_TYPE_MESSAGE);
        return null;
      }

      // Convert every raster input to WebP before measuring size or
      // hitting the wire. WebP inputs and other passthrough mime types
      // skip this branch.
      let fileToUpload = file;
      if (isRasterInputMimeType(file.type)) {
        try {
          fileToUpload = await convertToWebP(file);
        } catch {
          setError(CONVERSION_FAILED_MESSAGE);
          return null;
        }
      }

      if (fileToUpload.size > MAX_FILE_SIZE_BYTES) {
        setError(SIZE_LIMIT_MESSAGE);
        return null;
      }

      setIsUploading(true);
      try {
        const response = await mediaApi.upload(fileToUpload);
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
