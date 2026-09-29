package com.yaga.media.application.service;

import com.yaga.media.application.dto.MediaUploadResponse;
import com.yaga.media.application.usecase.UploadMediaUseCase;
import com.yaga.media.domain.exception.MediaUploadException;
import com.yaga.media.domain.model.MediaFile;
import com.yaga.media.domain.repository.MediaRepository;
import com.yaga.media.infrastructure.storage.S3StorageService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class MediaService implements UploadMediaUseCase {

  private final S3StorageService s3StorageService;
  private final MediaRepository mediaRepository;
  private final long maxFileSize;
  private final String allowedMimeTypes;

  @Inject
  public MediaService(
      S3StorageService s3StorageService,
      MediaRepository mediaRepository,
      @ConfigProperty(name = "media.max-file-size") long maxFileSize,
      @ConfigProperty(name = "media.allowed-mime-types") String allowedMimeTypes) {
    this.s3StorageService = s3StorageService;
    this.mediaRepository = mediaRepository;
    this.maxFileSize = maxFileSize;
    this.allowedMimeTypes = allowedMimeTypes;
  }

  @Override
  public MediaUploadResponse upload(
      String userId, String fileName, byte[] fileContent, String mimeType) {
    validateMimeType(mimeType);
    validateFileSize(fileContent.length);

    String storageKey = generateStorageKey(userId, fileName);

    String fileUrl = s3StorageService.uploadFile(storageKey, fileContent, mimeType);

    MediaFile mediaFile =
        new MediaFile(
            UUID.randomUUID().toString(),
            userId,
            storageKey,
            fileUrl,
            mimeType,
            fileContent.length,
            Instant.now());

    MediaFile saved = mediaRepository.save(mediaFile);

    Log.infof("Media uploaded successfully: userId=%s, id=%s", userId, saved.id());
    return MediaUploadResponse.fromDomain(saved);
  }

  private void validateMimeType(String mimeType) {
    String[] allowed = allowedMimeTypes.split(",");
    for (String pattern : allowed) {
      pattern = pattern.trim();
      if (mimeType.matches(pattern.replace("*", ".*"))) {
        return;
      }
    }
    throw new MediaUploadException(
        String.format(
            "MIME type '%s' is not allowed. Allowed types: %s", mimeType, allowedMimeTypes));
  }

  private void validateFileSize(long fileSizeBytes) {
    if (fileSizeBytes > maxFileSize) {
      throw new MediaUploadException(
          String.format(
              "File size %d bytes exceeds maximum allowed size of %d bytes",
              fileSizeBytes, maxFileSize));
    }
  }

  private String generateStorageKey(String userId, String fileName) {
    String extension = extractExtension(fileName);
    return String.format("media/%s/%s%s", userId, UUID.randomUUID(), extension);
  }

  private String extractExtension(String fileName) {
    int lastDot = fileName.lastIndexOf('.');
    if (lastDot > 0) {
      return fileName.substring(lastDot);
    }
    return "";
  }
}
