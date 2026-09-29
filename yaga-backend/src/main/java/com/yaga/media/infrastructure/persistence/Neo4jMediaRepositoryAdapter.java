package com.yaga.media.infrastructure.persistence;

import com.yaga.media.domain.exception.MediaUploadException;
import com.yaga.media.domain.model.MediaFile;
import com.yaga.media.domain.repository.MediaRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;

@ApplicationScoped
public class Neo4jMediaRepositoryAdapter implements MediaRepository {

  private final Driver driver;

  @Inject
  public Neo4jMediaRepositoryAdapter(Driver driver) {
    this.driver = driver;
  }

  @Override
  public MediaFile save(MediaFile mediaFile) {
    try (Session session = driver.session()) {
      session.executeWrite(
          tx ->
              tx.run(
                      "CREATE (m:MediaFile {"
                          + "id: $id, "
                          + "userId: $userId, "
                          + "storageKey: $storageKey, "
                          + "fileUrl: $fileUrl, "
                          + "mimeType: $mimeType, "
                          + "fileSizeBytes: $fileSizeBytes, "
                          + "uploadedAt: $uploadedAt"
                          + "})",
                      Values.parameters(
                          "id", mediaFile.id(),
                          "userId", mediaFile.userId(),
                          "storageKey", mediaFile.storageKey(),
                          "fileUrl", mediaFile.fileUrl(),
                          "mimeType", mediaFile.mimeType(),
                          "fileSizeBytes", mediaFile.fileSizeBytes(),
                          "uploadedAt", mediaFile.uploadedAt().toString()))
                  .consume());

      Log.infof("MediaFile saved to Neo4j: id=%s, userId=%s", mediaFile.id(), mediaFile.userId());
      return mediaFile;
    } catch (Exception e) {
      Log.errorf(e, "Failed to save MediaFile to Neo4j: id=%s", mediaFile.id());
      throw new MediaUploadException("Failed to persist media file metadata", e);
    }
  }
}
