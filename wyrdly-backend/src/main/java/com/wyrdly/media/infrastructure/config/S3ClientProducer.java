package com.wyrdly.media.infrastructure.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.net.URI;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;

@ApplicationScoped
public class S3ClientProducer {

  @Produces
  @Singleton
  public S3Client produceS3Client(
      @ConfigProperty(name = "rustfs.endpoint") String endpoint,
      @ConfigProperty(name = "rustfs.access-key") String accessKey,
      @ConfigProperty(name = "rustfs.secret-key") String secretKey) {

    return S3Client.builder()
        .endpointOverride(URI.create(endpoint))
        .region(software.amazon.awssdk.regions.Region.US_EAST_1)
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
        .forcePathStyle(true)
        .build();
  }
}
