import { useEffect, useState, type FC } from "react";

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
    let revoked: string | null = null;
    let cancelled = false;

    if (!src) {
      setBlobUrl(null);
      return () => {
        if (revoked) URL.revokeObjectURL(revoked);
      };
    }

    if (!isOwnMediaUrl(src)) {
      setBlobUrl(null);
      return () => {
        if (revoked) URL.revokeObjectURL(revoked);
      };
    }

    const token = localStorage.getItem("wyrdly_token");
    if (!token) {
      setBlobUrl(null);
      return () => {
        if (revoked) URL.revokeObjectURL(revoked);
      };
    }

    fetch(src, { headers: { Authorization: `Bearer ${token}` } })
      .then(async (response) => {
        if (!response.ok) {
          throw new Error(`Failed to fetch media: ${response.status}`);
        }
        const blob = await response.blob();
        if (cancelled) return;
        const newBlobUrl = URL.createObjectURL(blob);
        revoked = newBlobUrl;
        setBlobUrl(newBlobUrl);
      })
      .catch(() => {
        if (!cancelled) {
          setBlobUrl(null);
        }
      });

    return () => {
      cancelled = true;
      if (revoked) {
        URL.revokeObjectURL(revoked);
      }
    };
  }, [src, fallbackSrc]);

  const resolvedSrc = blobUrl ?? fallbackSrc ?? null;

  // When the src is external (not /api/media) and we never needed a
  // blob URL, render <img src> directly with the original src so the
  // browser handles caching and lazy-loading the same way as before.
  if (src && !isOwnMediaUrl(src)) {
    return (
      <img
        src={src}
        alt={alt}
        className={className}
        loading="lazy"
        onError={onError}
        data-testid="auth-image"
      />
    );
  }

  if (!resolvedSrc) {
    return null;
  }

  return (
    <img
      src={resolvedSrc}
      alt={alt}
      className={className}
      loading="lazy"
      onError={onError}
      data-testid="auth-image"
    />
  );
};
