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
  public MediaUploadResponse upload(String userId, byte[] fileContent) {
    validateFileSize(fileContent.length);

    String mimeType = detectMimeType(fileContent);
    validateMimeType(mimeType);

    String storageKey = generateStorageKey();

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

  private String detectMimeType(byte[] fileContent) {
    if (fileContent.length < 4) {
      return "application/octet-stream";
    }

    byte[] header = new byte[Math.min(12, fileContent.length)];
    System.arraycopy(fileContent, 0, header, 0, header.length);

    // JPEG
    if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8) {
      return "image/jpeg";
    }

    // PNG
    if (header[0] == (byte) 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') {
      return "image/png";
    }

    // WebP
    if (header[0] == 'R'
        && header[1] == 'I'
        && header[2] == 'F'
        && header[3] == 'F'
        && header[8] == 'W'
        && header[9] == 'E'
        && header[10] == 'B'
        && header[11] == 'P') {
      return "image/webp";
    }

    // MP4
    if (header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p') {
      return "video/mp4";
    }

    return "application/octet-stream";
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

  private String generateStorageKey() {
    return String.format("posts/img_%s.jpg", UUID.randomUUID());
  }
}
