import { mediaClient } from "./mediaClient";
import type { MediaUploadResponse } from "../types/media";

export const mediaApi = {
  async upload(file: File): Promise<MediaUploadResponse> {
    const formData = new FormData();
    formData.append("file", file);
    const response = await mediaClient.post<MediaUploadResponse>(
      "/api/media/upload",
      formData,
    );
    return response.data;
  },
};
