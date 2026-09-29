import { renderHook, act } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { mediaApi } from "../api/media";
import { useMediaUpload } from "./useMediaUpload";
import { MAX_FILE_SIZE_BYTES } from "../types/media";
import type { MediaUploadResponse } from "../types/media";

vi.mock("../api/media", () => ({
  mediaApi: {
    upload: vi.fn(),
  },
}));

vi.mock("../utils/imageTransform", () => ({
  convertToWebP: vi.fn(),
  isRasterInputMimeType: vi.fn(),
}));

import { convertToWebP, isRasterInputMimeType } from "../utils/imageTransform";

const mockedUpload = vi.mocked(mediaApi.upload);
const mockedConvertToWebP = vi.mocked(convertToWebP);
const mockedIsRasterInputMimeType = vi.mocked(isRasterInputMimeType);

const successResponse: MediaUploadResponse = {
  fileUrl: "https://cdn.wyrdly.app/posts/img_abc.webp",
  storageKey: "posts/img_abc.webp",
  mimeType: "image/webp",
  fileSizeBytes: 2048,
  uploadedAt: "2026-01-15T10:00:00Z",
};

const PASSTHROUGH_RESPONSE: MediaUploadResponse = {
  ...successResponse,
  storageKey: "posts/img_passthrough.webp",
  fileUrl: "https://cdn.wyrdly.app/posts/img_passthrough.webp",
};

/** Helper: configure mocks so a raster input is treated as a no-op
 *  passthrough (the "happy" path for the hook without depending on
 *  browser canvas APIs). */
function stubRasterPassthrough(file: File): void {
  mockedIsRasterInputMimeType.mockImplementation(
    (mime: string) => mime === file.type,
  );
  mockedConvertToWebP.mockResolvedValue(
    new File(["webp"], file.name.replace(/\.[^.]+$/, ".webp"), {
      type: "image/webp",
    }),
  );
}

describe("useMediaUpload", () => {
  beforeEach(() => {
    mockedUpload.mockReset();
    mockedConvertToWebP.mockReset();
    mockedIsRasterInputMimeType.mockReset();
    // By default: nothing is a raster input, so no conversion happens.
    mockedIsRasterInputMimeType.mockReturnValue(false);
  });

  it("returns the upload response and clears error on success", async () => {
    mockedUpload.mockResolvedValueOnce(successResponse);
    // Use WebP directly so no raster conversion is needed.
    const file = new File(["x"], "ok.webp", { type: "image/webp" });

    const { result } = renderHook(() => useMediaUpload());

    let returned: MediaUploadResponse | null = null;
    await act(async () => {
      returned = await result.current.upload(file);
    });

    expect(returned).toEqual(successResponse);
    expect(result.current.error).toBeNull();
    expect(mockedUpload).toHaveBeenCalledTimes(1);
    expect(mockedConvertToWebP).not.toHaveBeenCalled();
  });

  it("converts raster inputs (png/jpeg/gif/bmp) to WebP before upload", async () => {
    mockedUpload.mockResolvedValueOnce(successResponse);
    const input = new File(["png-bytes"], "photo.png", { type: "image/png" });
    const converted = new File(["webp-bytes"], "photo.webp", {
      type: "image/webp",
    });
    mockedIsRasterInputMimeType.mockReturnValue(true);
    mockedConvertToWebP.mockResolvedValueOnce(converted);

    const { result } = renderHook(() => useMediaUpload());

    let returned: MediaUploadResponse | null = null;
    await act(async () => {
      returned = await result.current.upload(input);
    });

    expect(returned).toEqual(successResponse);
    expect(mockedConvertToWebP).toHaveBeenCalledTimes(1);
    expect(mockedConvertToWebP).toHaveBeenCalledWith(input);
    expect(mockedUpload).toHaveBeenCalledWith(converted);
  });

  it("reports a conversion failure without calling the API", async () => {
    mockedIsRasterInputMimeType.mockReturnValue(true);
    mockedConvertToWebP.mockRejectedValueOnce(new Error("decoder exploded"));
    const input = new File(["x"], "broken.png", { type: "image/png" });

    const { result } = renderHook(() => useMediaUpload());

    let returned: MediaUploadResponse | null = "sentinel" as unknown as null;
    await act(async () => {
      returned = await result.current.upload(input);
    });

    expect(returned).toBeNull();
    expect(result.current.error).toBe(
      "Could not convert image to WebP. Try a different file.",
    );
    expect(mockedUpload).not.toHaveBeenCalled();
  });

  it("validates the converted file size, not the original input size", async () => {
    mockedIsRasterInputMimeType.mockReturnValue(true);
    const oversizedConverted = new File(
      [new Uint8Array(MAX_FILE_SIZE_BYTES + 1)],
      "huge.webp",
      { type: "image/webp" },
    );
    mockedConvertToWebP.mockResolvedValueOnce(oversizedConverted);
    const input = new File(["small"], "tiny.png", { type: "image/png" });

    const { result } = renderHook(() => useMediaUpload());

    let returned: MediaUploadResponse | null = "sentinel" as unknown as null;
    await act(async () => {
      returned = await result.current.upload(input);
    });

    expect(returned).toBeNull();
    expect(result.current.error).toBe("File exceeds 10MB limit");
    expect(mockedUpload).not.toHaveBeenCalled();
  });

  it("rejects files larger than MAX_FILE_SIZE_BYTES without calling the API", async () => {
    const oversized = new File(
      [new Uint8Array(MAX_FILE_SIZE_BYTES + 1)],
      "huge.webp",
      { type: "image/webp" },
    );

    const { result } = renderHook(() => useMediaUpload());

    let returned: MediaUploadResponse | null = "sentinel" as unknown as null;
    await act(async () => {
      returned = await result.current.upload(oversized);
    });

    expect(returned).toBeNull();
    expect(result.current.error).toBe("File exceeds 10MB limit");
    expect(mockedUpload).not.toHaveBeenCalled();
  });

  it("rejects unsupported MIME types without calling the API", async () => {
    const pdf = new File(["%PDF-1.4"], "doc.pdf", { type: "application/pdf" });

    const { result } = renderHook(() => useMediaUpload());

    let returned: MediaUploadResponse | null = "sentinel" as unknown as null;
    await act(async () => {
      returned = await result.current.upload(pdf);
    });

    expect(returned).toBeNull();
    expect(result.current.error).toBe(
      "Unsupported file type. Use JPEG, PNG, GIF, BMP, or WebP",
    );
    expect(mockedUpload).not.toHaveBeenCalled();
  });

  it("treats files with empty MIME type as unsupported", async () => {
    const unknown = new File(["data"], "blob", { type: "" });

    const { result } = renderHook(() => useMediaUpload());

    let returned: MediaUploadResponse | null = "sentinel" as unknown as null;
    await act(async () => {
      returned = await result.current.upload(unknown);
    });

    expect(returned).toBeNull();
    expect(result.current.error).toBe(
      "Unsupported file type. Use JPEG, PNG, GIF, BMP, or WebP",
    );
    expect(mockedUpload).not.toHaveBeenCalled();
  });

  it("sets error and returns null when the API throws", async () => {
    mockedUpload.mockRejectedValueOnce(new Error("network down"));
    const file = new File(["x"], "ok.webp", { type: "image/webp" });

    const { result } = renderHook(() => useMediaUpload());

    let returned: MediaUploadResponse | null = "sentinel" as unknown as null;
    await act(async () => {
      returned = await result.current.upload(file);
    });

    expect(returned).toBeNull();
    expect(result.current.error).toBe("network down");
    expect(mockedUpload).toHaveBeenCalledTimes(1);
  });

  it("uses a fallback error message when the rejection has no message", async () => {
    mockedUpload.mockRejectedValueOnce("string-error");
    const file = new File(["x"], "ok.webp", { type: "image/webp" });

    const { result } = renderHook(() => useMediaUpload());

    let returned: MediaUploadResponse | null = "sentinel" as unknown as null;
    await act(async () => {
      returned = await result.current.upload(file);
    });

    expect(returned).toBeNull();
    expect(result.current.error).toBe("Failed to upload file");
  });

  it("reports isUploading=true during an in-flight upload and resets after", async () => {
    let resolveUpload: ((value: MediaUploadResponse) => void) | undefined;
    const pending = new Promise<MediaUploadResponse>((resolve) => {
      resolveUpload = resolve;
    });
    mockedUpload.mockReturnValueOnce(pending);
    const file = new File(["x"], "ok.webp", { type: "image/webp" });

    const { result } = renderHook(() => useMediaUpload());

    let uploadPromise: Promise<MediaUploadResponse | null> | undefined;
    act(() => {
      uploadPromise = result.current.upload(file);
    });

    expect(result.current.isUploading).toBe(true);

    await act(async () => {
      resolveUpload?.(successResponse);
      await uploadPromise;
    });

    expect(result.current.isUploading).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it("clears a previous error on a successful subsequent upload", async () => {
    mockedUpload.mockRejectedValueOnce(new Error("first failure"));
    mockedUpload.mockResolvedValueOnce(PASSTHROUGH_RESPONSE);

    const { result } = renderHook(() => useMediaUpload());
    const file = new File(["x"], "ok.webp", { type: "image/webp" });

    await act(async () => {
      await result.current.upload(file);
    });
    expect(result.current.error).toBe("first failure");

    await act(async () => {
      await result.current.upload(file);
    });
    expect(result.current.error).toBeNull();
  });
});