package com.wyrdly.media.application.dto;

import com.wyrdly.media.domain.model.MediaFile;
import java.time.Instant;

public record MediaUploadResponse(
    String fileUrl, String storageKey, String mimeType, long fileSizeBytes, Instant uploadedAt) {

  public static MediaUploadResponse fromDomain(MediaFile mediaFile) {
    return new MediaUploadResponse(
        mediaFile.fileUrl(),
        mediaFile.storageKey(),
        mediaFile.mimeType(),
        mediaFile.fileSizeBytes(),
        mediaFile.uploadedAt());
  }
}
