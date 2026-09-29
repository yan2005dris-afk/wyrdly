import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("./axios", () => ({
  apiClient: {
    post: vi.fn(),
  },
}));

import { apiClient } from "./axios";
import { mediaApi } from "./media";
import type { MediaUploadResponse } from "../types/media";

const mockedPost = vi.mocked(apiClient.post);

const successResponse: MediaUploadResponse = {
  fileUrl: "https://cdn.wyrdly.app/posts/img_abc123.jpg",
  storageKey: "posts/img_abc123.jpg",
  mimeType: "image/jpeg",
  fileSizeBytes: 1024,
  uploadedAt: "2026-01-15T10:00:00Z",
};

describe("mediaApi.upload", () => {
  beforeEach(() => {
    mockedPost.mockReset();
  });

  it("POSTs FormData to /api/media/upload under the 'file' key", async () => {
    mockedPost.mockResolvedValueOnce({ data: successResponse });
    const file = new File(["binary"], "photo.jpg", { type: "image/jpeg" });

    const result = await mediaApi.upload(file);

    expect(mockedPost).toHaveBeenCalledTimes(1);
    const [urlArg, bodyArg, configArg] = mockedPost.mock.calls[0];
    expect(urlArg).toBe("/api/media/upload");
    expect(bodyArg).toBeInstanceOf(FormData);
    expect((bodyArg as FormData).get("file")).toBe(file);
    // Verify no manual Content-Type override — axios must add the multipart boundary itself.
    expect(configArg?.headers?.["Content-Type"]).toBeUndefined();
    expect(result).toEqual(successResponse);
  });

  it("does not pass a third argument (config) that sets Content-Type", async () => {
    mockedPost.mockResolvedValueOnce({ data: successResponse });
    const file = new File(["x"], "clip.mp4", { type: "video/mp4" });

    await mediaApi.upload(file);

    const callArgs = mockedPost.mock.calls[0];
    expect(callArgs.length).toBeLessThanOrEqual(2);
    const configArg = callArgs[2];
    if (configArg !== undefined) {
      expect(
        (configArg as { headers?: Record<string, unknown> }).headers?.[
          "Content-Type"
        ],
      ).toBeUndefined();
    }
  });

  it("returns the resolved response data from the API client", async () => {
    mockedPost.mockResolvedValueOnce({ data: successResponse });
    const file = new File(["a"], "pic.png", { type: "image/png" });

    const result = await mediaApi.upload(file);

    expect(result.fileUrl).toBe(successResponse.fileUrl);
    expect(result.storageKey).toBe(successResponse.storageKey);
    expect(result.mimeType).toBe("image/jpeg");
    expect(result.fileSizeBytes).toBe(1024);
  });
});
