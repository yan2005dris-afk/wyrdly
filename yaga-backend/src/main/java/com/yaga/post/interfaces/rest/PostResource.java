package com.yaga.post.interfaces.rest;

import com.yaga.post.application.dto.CreatePostRequest;
import com.yaga.post.application.dto.PostResponse;
import com.yaga.post.application.usecase.CreatePostUseCase;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Objects;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/api/posts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class PostResource {

  private final CreatePostUseCase createPostUseCase;
  private final JsonWebToken jwt;

  @Inject
  public PostResource(CreatePostUseCase createPostUseCase, JsonWebToken jwt) {
    this.createPostUseCase =
        Objects.requireNonNull(createPostUseCase, "createPostUseCase must not be null");
    this.jwt = Objects.requireNonNull(jwt, "jwt must not be null");
  }

  @POST
  @Authenticated
  public Response createPost(@Valid CreatePostRequest request) {
    String userId = jwt.getSubject();
    PostResponse response = createPostUseCase.createPost(userId, request);
    return Response.status(Response.Status.CREATED).entity(response).build();
  }
}
