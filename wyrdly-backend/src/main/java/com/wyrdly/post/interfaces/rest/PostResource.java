package com.wyrdly.post.interfaces.rest;

import com.wyrdly.post.application.dto.CreatePostRequest;
import com.wyrdly.post.application.dto.PostResponse;
import com.wyrdly.post.application.dto.ReactPostRequest;
import com.wyrdly.post.application.dto.ReactPostResponse;
import com.wyrdly.post.application.usecase.CreatePostUseCase;
import com.wyrdly.post.application.usecase.ReactToPostUseCase;
import com.wyrdly.post.domain.model.ReactionResult;
import io.micrometer.core.annotation.Timed;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/api/posts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class PostResource {

  private final CreatePostUseCase createPostUseCase;
  private final ReactToPostUseCase reactToPostUseCase;
  private final JsonWebToken jwt;

  @Inject
  public PostResource(
      CreatePostUseCase createPostUseCase,
      ReactToPostUseCase reactToPostUseCase,
      JsonWebToken jwt) {
    this.createPostUseCase =
        Objects.requireNonNull(createPostUseCase, "createPostUseCase must not be null");
    this.reactToPostUseCase =
        Objects.requireNonNull(reactToPostUseCase, "reactToPostUseCase must not be null");
    this.jwt = Objects.requireNonNull(jwt, "jwt must not be null");
  }

  @POST
  @Authenticated
  public Response createPost(@Valid CreatePostRequest request) {
    String userId = jwt.getSubject();
    PostResponse response = createPostUseCase.createPost(userId, request);
    return Response.status(Response.Status.CREATED).entity(response).build();
  }

  /**
   * Toggles a reaction on a post.
   *
   * <p>The endpoint always returns {@code 200 OK}. The body's {@code status} field communicates
   * whether the reaction was {@code ADDED}, {@code REMOVED} (toggle identical), or {@code UPDATED}
   * (toggle different). Rate limiting and idempotency are enforced upstream by the JAX-RS filters
   * {@link ReactionRateLimitFilter} and {@link ReactionIdempotencyFilter}.
   */
  @POST
  @Path("/{postId}/react")
  @Authenticated
  @CircuitBreaker(
      requestVolumeThreshold = 20,
      failureRatio = 0.5,
      delay = 10,
      delayUnit = ChronoUnit.SECONDS)
  @Timed(value = "reaction_duration_seconds", description = "Reaction endpoint latency", histogram = true)
  public Response react(@PathParam("postId") String postId, @Valid ReactPostRequest request) {
    String userId = jwt.getSubject();
    ReactionResult result = reactToPostUseCase.react(userId, postId, request.type());
    ReactPostResponse response =
        new ReactPostResponse(result.status(), result.reactionType(), result.totalReactions());
    return Response.ok(response).build();
  }
}
