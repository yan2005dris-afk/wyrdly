import { useEffect, useMemo, useState, type FC } from "react";

/**
 * Like a regular <img>, but if `src` points at our own /api/media/{id}
 * endpoint it fetches the bytes with the JWT and renders them as a
 * Blob URL. Browsers never send the Authorization header on <img> tags
 * so we have to do the fetch in JS and hand the renderer a Blob.
 *
 * External URLs (e.g. seed.cypher hardcoded avatar URLs, or future
 * media hosted on a third-party CDN) fall through to a plain
 * <img src> so the browser can cache and refetch them with no template
 * work in the way.
 *
 * The Blob URL is revoked on unmount or when src changes, so memory
 * does not leak across navigation.
 */
export interface AuthImageProps {
  readonly src: string | null | undefined;
  readonly alt: string;
  readonly className?: string;
  readonly fallbackSrc?: string;
  readonly onError?: () => void;
}

function isOwnMediaUrl(src: string): boolean {
  return /\/api\/media\/[A-Za-z0-9_-]+/.test(src);
}

export const AuthImage: FC<AuthImageProps> = ({
  src,
  alt,
  className,
  fallbackSrc,
  onError,
}) => {
  const [blobUrl, setBlobUrl] = useState<string | null>(null);

  useEffect(() => {
    if (!src || !isOwnMediaUrl(src)) {
      return;
    }

    const token = localStorage.getItem("wyrdly_token");
    if (!token) {
      return;
    }

    let cancelled = false;
    fetch(src, { headers: { Authorization: `Bearer ${token}` } })
      .then(async (response) => {
        if (!response.ok) {
          throw new Error(`Failed to fetch media: ${response.status}`);
        }
        const blob = await response.blob();
        if (cancelled) return;
        // setState inside an async callback fires only when the fetch
        // lifecycle completes. No cascading-render risk.
        setBlobUrl(URL.createObjectURL(blob));
      })
      .catch(() => {
        if (!cancelled) {
          // Same reasoning as above: fires from a Promise rejection.
          setBlobUrl(null);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [src]);

  // Revoke the previous blob URL whenever a new one takes its place
  // or the component unmounts.
  useEffect(() => {
    return () => {
      if (blobUrl) {
        URL.revokeObjectURL(blobUrl);
      }
    };
  }, [blobUrl]);

  // Derive the rendered src. External URLs go straight through; our
  // own /api/media URLs wait for the blob to be fetched.
  const effectiveSrc = useMemo(() => {
    if (src && !isOwnMediaUrl(src)) {
      return src;
    }
    return blobUrl ?? fallbackSrc ?? null;
  }, [src, blobUrl, fallbackSrc]);

  if (!effectiveSrc) {
    return null;
  }

  return (
    <img
      src={effectiveSrc}
      alt={alt}
      className={className}
      loading="lazy"
      onError={onError}
      data-testid="auth-image"
    />
  );
};