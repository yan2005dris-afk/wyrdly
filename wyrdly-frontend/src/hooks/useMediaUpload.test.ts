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

const mockedUpload = vi.mocked(mediaApi.upload);

const successResponse: MediaUploadResponse = {
  fileUrl: "https://cdn.wyrdly.app/posts/img_abc.jpg",
  storageKey: "posts/img_abc.jpg",
  mimeType: "image/jpeg",
  fileSizeBytes: 2048,
  uploadedAt: "2026-01-15T10:00:00Z",
};

describe("useMediaUpload", () => {
  beforeEach(() => {
    mockedUpload.mockReset();
  });

  it("returns the upload response and clears error on success", async () => {
    mockedUpload.mockResolvedValueOnce(successResponse);
    const file = new File(["x"], "ok.jpg", { type: "image/jpeg" });

    const { result } = renderHook(() => useMediaUpload());

    let returned: MediaUploadResponse | null = null;
    await act(async () => {
      returned = await result.current.upload(file);
    });

    expect(returned).toEqual(successResponse);
    expect(result.current.error).toBeNull();
    expect(mockedUpload).toHaveBeenCalledTimes(1);
    expect(mockedUpload).toHaveBeenCalledWith(file);
  });

  it("rejects files larger than MAX_FILE_SIZE_BYTES without calling the API", async () => {
    const { result } = renderHook(() => useMediaUpload());

    // 1 byte over the limit
    const oversized = new File(
      [new Uint8Array(MAX_FILE_SIZE_BYTES + 1)],
      "huge.jpg",
      { type: "image/jpeg" },
    );

    let returned: MediaUploadResponse | null = "sentinel" as unknown as null;
    await act(async () => {
      returned = await result.current.upload(oversized);
    });

    expect(returned).toBeNull();
    expect(result.current.error).toBe("File exceeds 10MB limit");
    expect(mockedUpload).not.toHaveBeenCalled();
  });

  it("rejects unsupported MIME types without calling the API", async () => {
    const { result } = renderHook(() => useMediaUpload());

    const pdf = new File(["%PDF-1.4"], "doc.pdf", { type: "application/pdf" });

    let returned: MediaUploadResponse | null = "sentinel" as unknown as null;
    await act(async () => {
      returned = await result.current.upload(pdf);
    });

    expect(returned).toBeNull();
    expect(result.current.error).toBe(
      "Unsupported file type. Use JPEG, PNG, WebP, or MP4",
    );
    expect(mockedUpload).not.toHaveBeenCalled();
  });

  it("treats files with empty MIME type as unsupported", async () => {
    const { result } = renderHook(() => useMediaUpload());

    const unknown = new File(["data"], "blob", { type: "" });

    let returned: MediaUploadResponse | null = "sentinel" as unknown as null;
    await act(async () => {
      returned = await result.current.upload(unknown);
    });

    expect(returned).toBeNull();
    expect(result.current.error).toBe(
      "Unsupported file type. Use JPEG, PNG, WebP, or MP4",
    );
    expect(mockedUpload).not.toHaveBeenCalled();
  });

  it("sets error and returns null when the API throws", async () => {
    mockedUpload.mockRejectedValueOnce(new Error("network down"));
    const file = new File(["x"], "ok.jpg", { type: "image/jpeg" });

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
    const file = new File(["x"], "ok.jpg", { type: "image/jpeg" });

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
    const file = new File(["x"], "ok.jpg", { type: "image/jpeg" });

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
    mockedUpload.mockResolvedValueOnce(successResponse);

    const { result } = renderHook(() => useMediaUpload());
    const file = new File(["x"], "ok.jpg", { type: "image/jpeg" });

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
