export interface MediaUploadResponse {
  readonly fileUrl: string;
  readonly storageKey: string;
  readonly mimeType: string;
  readonly fileSizeBytes: number;
  readonly uploadedAt: string;
}

export const MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;

/**
 * Frontend input allowlist. The backend only accepts `image/webp`
 * because every accepted input here is converted to WebP before
 * upload. Video (mp4) is intentionally excluded and will land in a
 * follow-up ticket with a separate convertVideo pipeline.
 */
export const ALLOWED_MIME_TYPES = [
  "image/jpeg",
  "image/png",
  "image/gif",
  "image/bmp",
  "image/webp",
] as const;

export type AllowedMimeType = (typeof ALLOWED_MIME_TYPES)[number];

export function isAllowedMimeType(mime: string): mime is AllowedMimeType {
  return (ALLOWED_MIME_TYPES as readonly string[]).includes(mime);
}
