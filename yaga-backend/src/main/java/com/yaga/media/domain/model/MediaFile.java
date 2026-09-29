package com.yaga.media.domain.model;

import java.time.Instant;

public record MediaFile(
    String id,
    String userId,
    String storageKey,
    String fileUrl,
    String mimeType,
    long fileSizeBytes,
    Instant uploadedAt) {

  public MediaFile {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("id must not be blank");
    }
    if (userId == null || userId.isBlank()) {
      throw new IllegalArgumentException("userId must not be blank");
    }
    if (storageKey == null || storageKey.isBlank()) {
      throw new IllegalArgumentException("storageKey must not be blank");
    }
    if (fileUrl == null || fileUrl.isBlank()) {
      throw new IllegalArgumentException("fileUrl must not be blank");
    }
    if (mimeType == null || mimeType.isBlank()) {
      throw new IllegalArgumentException("mimeType must not be blank");
    }
    if (fileSizeBytes < 0) {
      throw new IllegalArgumentException("fileSizeBytes must be >= 0");
    }
    if (uploadedAt == null) {
      throw new IllegalArgumentException("uploadedAt must not be null");
    }
  }
}
