package com.yaga.media.interfaces.rest;

import com.yaga.media.application.dto.MediaUploadResponse;
import com.yaga.media.application.usecase.UploadMediaUseCase;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
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
  private final JsonWebToken jwt;

  @Inject
  public MediaResource(UploadMediaUseCase uploadMediaUseCase, JsonWebToken jwt) {
    this.uploadMediaUseCase =
        Objects.requireNonNull(uploadMediaUseCase, "uploadMediaUseCase must not be null");
    this.jwt = Objects.requireNonNull(jwt, "jwt must not be null");
  }

  @POST
  @Path("/upload")
  @Authenticated
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public Response uploadMedia(
      @RestForm("file") InputStream fileInputStream,
      @RestForm("fileName") String fileName,
      @RestForm("mimeType") String mimeType) {
    try {
      String userId = jwt.getSubject();
      byte[] fileContent = fileInputStream.readAllBytes();

      MediaUploadResponse response =
          uploadMediaUseCase.upload(userId, fileName, fileContent, mimeType);

      return Response.status(Response.Status.CREATED).entity(response).build();
    } catch (Exception e) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(new ErrorResponse(e.getMessage()))
          .build();
    }
  }

  record ErrorResponse(String error) {}
}
