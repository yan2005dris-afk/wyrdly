/**
 * Image transform utilities for the media upload pipeline.
 *
 * Every raster image accepted by the frontend (PNG / JPEG / GIF / BMP)
 * is converted to WebP before it leaves the browser. This shrinks the
 * bytes that travel over the wire and that we keep in storage while
 * preserving the visual quality required for a social-media feed.
 */

export const WEBP_QUALITY = 0.8;

const RASTER_INPUT_MIME_TYPES = [
  "image/png",
  "image/jpeg",
  "image/gif",
  "image/bmp",
] as const;

export type RasterInputMimeType = (typeof RASTER_INPUT_MIME_TYPES)[number];

export function isRasterInputMimeType(
  mime: string,
): mime is RasterInputMimeType {
  return (RASTER_INPUT_MIME_TYPES as readonly string[]).includes(mime);
}

/**
 * Loads an arbitrary image File into an HTMLImageElement. Resolves
 * with the decoded image or rejects if the browser cannot decode the
 * file (e.g. unsupported codec, corrupt bytes).
 *
 * Exposed via `imageTransformOptions.decodeImage` for unit tests so
 * jsdom does not need to fully implement `URL.createObjectURL`.
 */
export async function decodeImage(file: File): Promise<HTMLImageElement> {
  const url = URL.createObjectURL(file);
  try {
    const img = new Image();
    return await new Promise<HTMLImageElement>((resolve, reject) => {
      img.onload = () => resolve(img);
      img.onerror = () => reject(new Error("Failed to decode image"));
      img.src = url;
    });
  } finally {
    URL.revokeObjectURL(url);
  }
}

/**
 * Paints `image` into a canvas of `width` x `height` and exports the
 * pixels as a WebP Blob. Prefers `OffscreenCanvas` when available,
 * falls back to a detached `<canvas>` element.
 */
async function rasterizeToWebP(
  image: HTMLImageElement,
): Promise<Blob> {
  const width = image.naturalWidth;
  const height = image.naturalHeight;

  if (typeof OffscreenCanvas !== "undefined") {
    const canvas = new OffscreenCanvas(width, height);
    const ctx = canvas.getContext("2d");
    if (!ctx) throw new Error("OffscreenCanvas 2D context unavailable");
    ctx.drawImage(image, 0, 0);
    return canvas.convertToBlob({
      type: "image/webp",
      quality: WEBP_QUALITY,
    });
  }

  const canvas = document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext("2d");
  if (!ctx) throw new Error("Canvas 2D context unavailable");
  ctx.drawImage(image, 0, 0);

  return new Promise<Blob>((resolve, reject) => {
    canvas.toBlob(
      (blob) => {
        if (blob) resolve(blob);
        else reject(new Error("Canvas failed to produce WebP blob"));
      },
      "image/webp",
      WEBP_QUALITY,
    );
  });
}

/**
 * Convert a raster image File to a WebP File. Throws if the input is
 * not a supported raster mime type or if the browser cannot encode
 * WebP. The returned File keeps the original name with the `.webp`
 * extension so storage keys stay human-readable.
 */
export interface ConvertToWebPOptions {
  readonly decodeImage?: (file: File) => Promise<HTMLImageElement>;
  readonly rasterizeToWebP?: (image: HTMLImageElement) => Promise<Blob>;
  readonly supportsWebPEncoding?: () => boolean;
}

export async function convertToWebP(
  file: File,
  options: ConvertToWebPOptions = {},
): Promise<File> {
  if (!isRasterInputMimeType(file.type)) {
    throw new Error(
      `Unsupported raster mime type for WebP conversion: ${file.type}`,
    );
  }

  if (!(options.supportsWebPEncoding ?? supportsWebPEncoding)()) {
    throw new Error("Current browser does not support WebP encoding");
  }

  const decode = options.decodeImage ?? decodeImage;
  const raster = options.rasterizeToWebP ?? rasterizeToWebP;

  const image = await decode(file);
  const blob = await raster(image);

  const dotIndex = file.name.lastIndexOf(".");
  const baseName =
    dotIndex > 0 ? file.name.slice(0, dotIndex) : file.name;
  const newName = `${baseName}.webp`;

  return new File([blob], newName, {
    type: "image/webp",
    lastModified: Date.now(),
  });
}

/**
 * Cheap capability check used by the hook to decide whether to even
 * attempt the conversion. We probe the encoder with a 1x1 white
 * pixel; this works on every browser we support.
 */
export function supportsWebPEncoding(): boolean {
  if (typeof document === "undefined") return false;
  const canvas = document.createElement("canvas");
  canvas.width = 1;
  canvas.height = 1;
  const dataUrl = canvas.toDataURL("image/webp");
  return dataUrl.startsWith("data:image/webp");
}