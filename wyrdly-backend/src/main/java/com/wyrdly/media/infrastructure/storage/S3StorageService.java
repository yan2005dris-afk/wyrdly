package com.wyrdly.media.infrastructure.storage;

import com.wyrdly.media.domain.exception.MediaUploadException;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@ApplicationScoped
public class S3StorageService {

  private final S3Client s3Client;
  private final String bucketName;

  @Inject
  public S3StorageService(
      S3Client s3Client, @ConfigProperty(name = "media.bucket.name") String bucketName) {
    this.s3Client = s3Client;
    this.bucketName = bucketName;
  }

  /**
   * Upload bytes to the configured bucket under {@code storageKey} with
   * the given {@code mimeType}. The returned value is just the key —
   * callers (MediaService) build the public-facing URL themselves so
   * the bucket can stay 100% private.
   */
  public String uploadFile(String storageKey, byte[] fileContent, String mimeType) {
    try {
      PutObjectRequest putObjectRequest =
          PutObjectRequest.builder()
              .bucket(bucketName)
              .key(storageKey)
              .contentType(mimeType)
              .build();

      PutObjectResponse response =
          s3Client.putObject(
              putObjectRequest,
              software.amazon.awssdk.core.sync.RequestBody.fromBytes(fileContent));

      Log.infof(
          "File uploaded to S3: bucket=%s, key=%s, eTag=%s",
          bucketName, storageKey, response.eTag());
      return storageKey;
    } catch (Exception e) {
      Log.errorf(e, "Failed to upload file to S3: storageKey=%s", storageKey);
      throw new MediaUploadException("Failed to upload file to S3", e);
    }
  }

  /**
   * Open a streaming body from S3 for {@code storageKey}. The returned
   * stream is owned by the caller and must be closed (try-with-resources)
   * once the body has been drained.
   */
  public StoredObject downloadFile(String storageKey) {
    try {
      GetObjectRequest getObjectRequest =
          GetObjectRequest.builder().bucket(bucketName).key(storageKey).build();
      ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(getObjectRequest);
      GetObjectResponse metadata = stream.response();
      return new StoredObject(
          stream,
          metadata.contentType(),
          metadata.contentLength() != null ? metadata.contentLength() : -1L);
    } catch (Exception e) {
      Log.errorf(e, "Failed to read from S3: storageKey=%s", storageKey);
      throw new MediaUploadException("Failed to read media from storage", e);
    }
  }

  /**
   * Tuple of an open streaming body plus the S3-supplied
   * Content-Type and Content-Length. Callers must close the stream.
   */
  public record StoredObject(
      ResponseInputStream<GetObjectResponse> stream,
      String contentType,
      long contentLength) {}
}