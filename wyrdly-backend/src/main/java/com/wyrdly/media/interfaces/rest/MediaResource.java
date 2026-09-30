package com.wyrdly.media.interfaces.rest;

import com.wyrdly.media.application.dto.MediaUploadResponse;
import com.wyrdly.media.application.usecase.UploadMediaUseCase;
import com.wyrdly.media.domain.model.MediaFile;
import com.wyrdly.media.domain.repository.MediaRepository;
import com.wyrdly.media.infrastructure.storage.S3StorageService;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.InputStream;
import java.util.Objects;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.RestForm;

@Path("/api/media")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class MediaResource {

  private final UploadMediaUseCase uploadMediaUseCase;
  private final MediaRepository mediaRepository;
  private final S3StorageService s3StorageService;
  private final JsonWebToken jwt;

  @Inject
  public MediaResource(
      UploadMediaUseCase uploadMediaUseCase,
      MediaRepository mediaRepository,
      S3StorageService s3StorageService,
      JsonWebToken jwt) {
    this.uploadMediaUseCase =
        Objects.requireNonNull(uploadMediaUseCase, "uploadMediaUseCase must not be null");
    this.mediaRepository =
        Objects.requireNonNull(mediaRepository, "mediaRepository must not be null");
    this.s3StorageService =
        Objects.requireNonNull(s3StorageService, "s3StorageService must not be null");
    this.jwt = Objects.requireNonNull(jwt, "jwt must not be null");
  }

  @POST
  @Path("/upload")
  @Authenticated
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public Response uploadMedia(@RestForm("file") InputStream fileInputStream) {
    try {
      String userId = jwt.getSubject();
      byte[] fileContent = fileInputStream.readAllBytes();

      MediaUploadResponse response = uploadMediaUseCase.upload(userId, fileContent);

      return Response.status(Response.Status.CREATED).entity(response).build();
    } catch (Exception e) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(new ErrorResponse(e.getMessage()))
          .build();
    }
  }

  /**
   * Stream a media file's bytes through the backend. The bucket stays
   * 100% private — only this endpoint, authenticated via JWT, can
   * serve a media asset. Visibility per post is not yet enforced;
   * follow-up ticket will gate reads by post.visibility.
   */
  @GET
  @Path("/{id}")
  @Authenticated
  @Produces(MediaType.WILDCARD)
  public Response downloadMedia(@PathParam("id") String id) {
    MediaFile mediaFile = mediaRepository.findById(id).orElse(null);
    if (mediaFile == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new ErrorResponse("Media not found"))
          .build();
    }

    S3StorageService.StoredObject stored = s3StorageService.downloadFile(mediaFile.storageKey());

    Response.ResponseBuilder builder =
        Response.ok((InputStream) stored.stream())
            .header("Content-Type", mediaFile.mimeType());
    if (stored.contentLength() > 0) {
      builder.header("Content-Length", stored.contentLength());
    }
    return builder.build();
  }

  record ErrorResponse(String error) {}
}