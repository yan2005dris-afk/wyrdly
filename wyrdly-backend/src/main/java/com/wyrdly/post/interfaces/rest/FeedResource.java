package com.wyrdly.post.interfaces.rest;

import com.wyrdly.post.application.dto.FeedResponseDto;
import com.wyrdly.post.application.pagination.FeedPaginationRequest;
import com.wyrdly.post.application.usecase.GetFeedUseCase;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Objects;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/api/feed")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class FeedResource {

  private final GetFeedUseCase getFeedUseCase;
  private final JsonWebToken jwt;

  @Inject
  public FeedResource(GetFeedUseCase getFeedUseCase, JsonWebToken jwt) {
    this.getFeedUseCase = Objects.requireNonNull(getFeedUseCase, "getFeedUseCase must not be null");
    this.jwt = Objects.requireNonNull(jwt, "jwt must not be null");
  }

  @GET
  @Authenticated
  public Response getFeed(
      @QueryParam("cursor") String cursor,
      @QueryParam("limit") Integer limit,
      @QueryParam("page") Integer page,
      @QueryParam("pageSize") Integer pageSize) {
    String userId = jwt.getSubject();
    FeedPaginationRequest pagination =
        FeedPaginationRequest.fromParams(cursor, limit, page, pageSize);
    FeedResponseDto response = getFeedUseCase.getFeed(userId, pagination);
    return Response.ok(response).build();
  }
}
