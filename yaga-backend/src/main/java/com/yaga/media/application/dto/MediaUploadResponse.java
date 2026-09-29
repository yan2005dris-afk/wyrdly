package com.yaga.media.application.dto;

import com.yaga.media.domain.model.MediaFile;
import java.time.Instant;

public record MediaUploadResponse(
    String id, String fileUrl, String mimeType, long fileSizeBytes, Instant uploadedAt) {

  public static MediaUploadResponse fromDomain(MediaFile mediaFile) {
    return new MediaUploadResponse(
        mediaFile.id(),
        mediaFile.fileUrl(),
        mediaFile.mimeType(),
        mediaFile.fileSizeBytes(),
        mediaFile.uploadedAt());
  }
}
