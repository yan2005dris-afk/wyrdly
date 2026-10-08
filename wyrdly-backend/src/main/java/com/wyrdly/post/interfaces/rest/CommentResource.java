package com.wyrdly.post.interfaces.rest;

import com.wyrdly.post.application.dto.CommentListResponseDto;
import com.wyrdly.post.application.dto.CommentResponse;
import com.wyrdly.post.application.dto.CreateCommentRequest;
import com.wyrdly.post.application.usecase.CreateCommentUseCase;
import com.wyrdly.post.application.usecase.DeleteCommentUseCase;
import com.wyrdly.post.application.usecase.ListCommentsByPostUseCase;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Objects;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * JAX-RS resource for {@code /api/posts/{postId}/comments}. Maps the three comment verbs to the
 * segregated use case interfaces. Authentication is enforced declaratively via {@link
 * Authenticated} on the mutating endpoints; the GET listing is intentionally public so the feed can
 * hydrate comments without forcing the browser to send a JWT (the post itself is part of the
 * authenticated feed, so this does not leak data).
 */
@Path("/api/posts/{postId}/comments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class CommentResource {

  private final CreateCommentUseCase createCommentUseCase;
  private final ListCommentsByPostUseCase listCommentsByPostUseCase;
  private final DeleteCommentUseCase deleteCommentUseCase;
  private final JsonWebToken jwt;

  @Inject
  public CommentResource(
      CreateCommentUseCase createCommentUseCase,
      ListCommentsByPostUseCase listCommentsByPostUseCase,
      DeleteCommentUseCase deleteCommentUseCase,
      JsonWebToken jwt) {
    this.createCommentUseCase =
        Objects.requireNonNull(createCommentUseCase, "createCommentUseCase");
    this.listCommentsByPostUseCase =
        Objects.requireNonNull(listCommentsByPostUseCase, "listCommentsByPostUseCase");
    this.deleteCommentUseCase =
        Objects.requireNonNull(deleteCommentUseCase, "deleteCommentUseCase");
    this.jwt = Objects.requireNonNull(jwt, "jwt");
  }

  /**
   * Creates a new comment on the post identified by {@code postId}.
   *
   * <p>Returns {@code 201 Created} with the enriched comment body. Bean-validation failures on the
   * request body are converted to {@code 400 Bad Request} by {@link PostExceptionMappers}.
   */
  @POST
  @Authenticated
  public Response createComment(
      @PathParam("postId") String postId, @Valid CreateCommentRequest request) {
    String userId = jwt.getSubject();
    CommentResponse response = createCommentUseCase.createComment(postId, userId, request);
    return Response.status(Response.Status.CREATED).entity(response).build();
  }

  /**
   * Returns the comments for the post, ordered chronologically. Pagination defaults to page 1 with
   * 20 items per page.
   */
  @GET
  public Response getComments(
      @PathParam("postId") String postId,
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("pageSize") @DefaultValue("20") int pageSize) {
    CommentListResponseDto response = listCommentsByPostUseCase.getComments(postId, page, pageSize);
    return Response.ok(response).build();
  }

  /**
   * Deletes a comment by id. The {@code postId} path segment is verified by the service so that a
   * wrong post/comment pairing surfaces as 404 rather than 403 (no enumeration side-channel).
   */
  @DELETE
  @Path("/{commentId}")
  @Authenticated
  public Response deleteComment(
      @PathParam("postId") String postId, @PathParam("commentId") String commentId) {
    String userId = jwt.getSubject();
    deleteCommentUseCase.deleteComment(postId, commentId, userId);
    return Response.noContent().build();
  }
}
