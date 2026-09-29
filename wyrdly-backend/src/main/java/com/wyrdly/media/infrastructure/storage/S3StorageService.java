package com.wyrdly.media.infrastructure.storage;

import com.wyrdly.media.domain.exception.MediaUploadException;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.s3.S3Client;
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
      return String.format("s3://%s/%s", bucketName, storageKey);
    } catch (Exception e) {
      Log.errorf(e, "Failed to upload file to S3: storageKey=%s", storageKey);
      throw new MediaUploadException("Failed to upload file to S3", e);
    }
  }
}
