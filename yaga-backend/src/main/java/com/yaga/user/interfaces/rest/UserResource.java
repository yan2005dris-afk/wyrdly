package com.yaga.user.interfaces.rest;

import com.yaga.user.application.dto.UpdateProfileRequest;
import com.yaga.user.application.dto.UserProfileResponse;
import com.yaga.user.application.usecase.GetUserProfileUseCase;
import com.yaga.user.application.usecase.UpdateUserProfileUseCase;
import com.yaga.user.infrastructure.security.OptionalJwtSubjectExtractor;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Objects;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class UserResource {

  private final GetUserProfileUseCase getUserProfileUseCase;
  private final UpdateUserProfileUseCase updateUserProfileUseCase;
  private final OptionalJwtSubjectExtractor optionalJwtSubjectExtractor;
  private final JsonWebToken jwt;

  @Inject
  public UserResource(
      GetUserProfileUseCase getUserProfileUseCase,
      UpdateUserProfileUseCase updateUserProfileUseCase,
      OptionalJwtSubjectExtractor optionalJwtSubjectExtractor,
      JsonWebToken jwt) {
    this.getUserProfileUseCase =
        Objects.requireNonNull(getUserProfileUseCase, "getUserProfileUseCase must not be null");
    this.updateUserProfileUseCase =
        Objects.requireNonNull(
            updateUserProfileUseCase, "updateUserProfileUseCase must not be null");
    this.optionalJwtSubjectExtractor =
        Objects.requireNonNull(
            optionalJwtSubjectExtractor, "optionalJwtSubjectExtractor must not be null");
    this.jwt = Objects.requireNonNull(jwt, "jwt must not be null");
  }

  @GET
  @Path("/{username}")
  public Response getProfile(
      @PathParam("username") String username,
      @HeaderParam("Authorization") String authorizationHeader) {
    String viewerId = optionalJwtSubjectExtractor.extractSubject(authorizationHeader);
    UserProfileResponse response = getUserProfileUseCase.getProfile(username, viewerId);
    return Response.ok(response).build();
  }

  @PUT
  @Path("/profile")
  @Authenticated
  @Consumes(MediaType.APPLICATION_JSON)
  public Response updateProfile(@Valid UpdateProfileRequest request) {
    String userId = jwt.getSubject();
    UserProfileResponse response = updateUserProfileUseCase.updateProfile(userId, request);
    return Response.ok(response).build();
  }
}
