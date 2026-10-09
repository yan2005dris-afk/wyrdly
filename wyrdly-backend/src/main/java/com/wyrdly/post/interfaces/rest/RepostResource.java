package com.wyrdly.post.interfaces.rest;

import com.wyrdly.post.application.dto.RepostResponse;
import com.wyrdly.post.application.usecase.RepostPostUseCase;
import com.wyrdly.post.application.usecase.UndoRepostUseCase;
import io.micrometer.core.annotation.Timed;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Objects;
import org.eclipse.microprofile.jwt.JsonWebToken;

/** REST endpoint for reposting (boosting) and unreposting posts. */
@Path("/api/posts/{postId}/repost")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RepostResource {

  private final RepostPostUseCase repostPostUseCase;
  private final UndoRepostUseCase undoRepostUseCase;
  private final JsonWebToken jwt;

  @Inject
  public RepostResource(
      RepostPostUseCase repostPostUseCase, UndoRepostUseCase undoRepostUseCase, JsonWebToken jwt) {
    this.repostPostUseCase =
        Objects.requireNonNull(repostPostUseCase, "repostPostUseCase must not be null");
    this.undoRepostUseCase =
        Objects.requireNonNull(undoRepostUseCase, "undoRepostUseCase must not be null");
    this.jwt = Objects.requireNonNull(jwt, "jwt must not be null");
  }

  @PUT
  @Authenticated
  @Timed(
      value = "repost_duration_seconds",
      description = "Repost endpoint latency",
      histogram = true)
  public Response repost(@PathParam("postId") String postId) {
    String userId = jwt.getSubject();
    RepostResponse response = repostPostUseCase.repost(userId, postId);
    return Response.ok(response).build();
  }

  @DELETE
  @Authenticated
  @Timed(
      value = "repost_duration_seconds",
      description = "Repost endpoint latency",
      histogram = true)
  public Response unrepost(@PathParam("postId") String postId) {
    String userId = jwt.getSubject();
    RepostResponse response = undoRepostUseCase.unrepost(userId, postId);
    return Response.ok(response).build();
  }
}
