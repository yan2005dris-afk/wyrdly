package com.wyrdly.media.infrastructure.storage;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ApplicationScoped
public class BucketInitializer {

  private final S3Client s3Client;
  private final String bucketName;

  @Inject
  public BucketInitializer(
      S3Client s3Client, @ConfigProperty(name = "media.bucket.name") String bucketName) {
    this.s3Client = s3Client;
    this.bucketName = bucketName;
  }

  public void initializeOnStartup(@Observes StartupEvent event) {
    String profile = ConfigProvider.getConfig().getValue("quarkus.profile", String.class);
    if ("test".equals(profile)) {
      Log.debug("Skipping bucket initialization in test profile");
      return;
    }

    try {
      HeadBucketRequest headBucketRequest = HeadBucketRequest.builder().bucket(bucketName).build();
      s3Client.headBucket(headBucketRequest);
      Log.infof("Bucket '%s' already exists", bucketName);
    } catch (S3Exception e) {
      if (e.statusCode() == 404) {
        createBucket();
      } else {
        Log.warnf(e, "Failed to check bucket status: %s", bucketName);
      }
    }
  }

  private void createBucket() {
    try {
      CreateBucketRequest createBucketRequest =
          CreateBucketRequest.builder().bucket(bucketName).build();
      s3Client.createBucket(createBucketRequest);
      Log.infof("Bucket '%s' created successfully", bucketName);
    } catch (S3Exception e) {
      Log.warnf(e, "Failed to create bucket: %s", bucketName);
    }
  }
}
