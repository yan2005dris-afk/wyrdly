package com.wyrdly.media.infrastructure.persistence;

import com.wyrdly.media.domain.exception.MediaUploadException;
import com.wyrdly.media.domain.model.MediaFile;
import com.wyrdly.media.domain.repository.MediaRepository;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.Optional;
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

  @Override
  public Optional<MediaFile> findById(String id) {
    try (Session session = driver.session()) {
      var result =
          session.executeRead(
              tx ->
                  tx.run(
                          "MATCH (m:MediaFile {id: $id}) "
                              + "RETURN m.id AS id, m.userId AS userId, "
                              + "m.storageKey AS storageKey, m.fileUrl AS fileUrl, "
                              + "m.mimeType AS mimeType, m.fileSizeBytes AS fileSizeBytes, "
                              + "m.uploadedAt AS uploadedAt",
                          Values.parameters("id", id))
                      .single());

      if (result == null) {
        return Optional.empty();
      }

      MediaFile mediaFile =
              new MediaFile(
                  result.get("id").asString(),
                  result.get("userId").asString(),
                  result.get("storageKey").asString(),
                  result.get("fileUrl").asString(),
                  result.get("mimeType").asString(),
                  result.get("fileSizeBytes").asLong(),
                  Instant.parse(result.get("uploadedAt").asString()));
      return Optional.of(mediaFile);
    } catch (org.neo4j.driver.exceptions.NoSuchRecordException e) {
      return Optional.empty();
    } catch (Exception e) {
      Log.errorf(e, "Failed to look up MediaFile: id=%s", id);
      throw new MediaUploadException("Failed to look up media file metadata", e);
    }
  }
}