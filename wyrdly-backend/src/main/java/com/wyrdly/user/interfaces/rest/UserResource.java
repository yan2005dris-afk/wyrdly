package com.wyrdly.user.interfaces.rest;

import com.wyrdly.post.application.dto.PostResponse;
import com.wyrdly.user.application.dto.FollowActionResponse;
import com.wyrdly.user.application.dto.GraphSuggestionsResponse;
import com.wyrdly.user.application.dto.UpdateProfileRequest;
import com.wyrdly.user.application.dto.UserProfileResponse;
import com.wyrdly.user.application.dto.UserSearchResponseDto;
import com.wyrdly.user.application.dto.UserSearchResultDto;
import com.wyrdly.user.application.service.UserProfileService;
import com.wyrdly.user.application.usecase.FollowUserUseCase;
import com.wyrdly.user.application.usecase.GetSuggestionsUseCase;
import com.wyrdly.user.application.usecase.GetUserProfileUseCase;
import com.wyrdly.user.application.usecase.SearchUsersUseCase;
import com.wyrdly.user.application.usecase.UnfollowUserUseCase;
import com.wyrdly.user.application.usecase.UpdateUserProfileUseCase;
import com.wyrdly.user.infrastructure.security.OptionalJwtSubjectExtractor;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Objects;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class UserResource {

  private final GetUserProfileUseCase getUserProfileUseCase;
  private final UserProfileService userProfileService;
  private final UpdateUserProfileUseCase updateUserProfileUseCase;
  private final GetSuggestionsUseCase getSuggestionsUseCase;
  private final FollowUserUseCase followUserUseCase;
  private final UnfollowUserUseCase unfollowUserUseCase;
  private final SearchUsersUseCase searchUsersUseCase;
  private final OptionalJwtSubjectExtractor optionalJwtSubjectExtractor;
  private final JsonWebToken jwt;

  @Inject
  public UserResource(
      GetUserProfileUseCase getUserProfileUseCase,
      UserProfileService userProfileService,
      UpdateUserProfileUseCase updateUserProfileUseCase,
      GetSuggestionsUseCase getSuggestionsUseCase,
      FollowUserUseCase followUserUseCase,
      UnfollowUserUseCase unfollowUserUseCase,
      SearchUsersUseCase searchUsersUseCase,
      OptionalJwtSubjectExtractor optionalJwtSubjectExtractor,
      JsonWebToken jwt) {
    this.getUserProfileUseCase =
        Objects.requireNonNull(getUserProfileUseCase, "getUserProfileUseCase must not be null");
    this.userProfileService =
        Objects.requireNonNull(userProfileService, "userProfileService must not be null");
    this.updateUserProfileUseCase =
        Objects.requireNonNull(
            updateUserProfileUseCase, "updateUserProfileUseCase must not be null");
    this.getSuggestionsUseCase =
        Objects.requireNonNull(getSuggestionsUseCase, "getSuggestionsUseCase must not be null");
    this.followUserUseCase =
        Objects.requireNonNull(followUserUseCase, "followUserUseCase must not be null");
    this.unfollowUserUseCase =
        Objects.requireNonNull(unfollowUserUseCase, "unfollowUserUseCase must not be null");
    this.searchUsersUseCase =
        Objects.requireNonNull(searchUsersUseCase, "searchUsersUseCase must not be null");
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

  @GET
  @Path("/suggestions")
  @Authenticated
  public Response getSuggestions(
      @QueryParam("page") @DefaultValue("0") int page,
      @QueryParam("pageSize") @DefaultValue("10") int pageSize) {
    String userId = jwt.getSubject();
    GraphSuggestionsResponse response =
        getSuggestionsUseCase.getSuggestions(userId, page, pageSize);
    return Response.ok(response).build();
  }

  /**
   * Paginated list of posts authored by the user whose username is in the path. Returns 404 when
   * the username does not exist (mapped from {@link
   * com.wyrdly.user.domain.exception.UserProfileNotFoundException}).
   */
  @GET
  @Path("/{username}/posts")
  public Response getUserPosts(
      @PathParam("username") String username,
      @HeaderParam("Authorization") String authorizationHeader,
      @QueryParam("page") @DefaultValue("0") int page,
      @QueryParam("pageSize") @DefaultValue("20") int pageSize) {
    String viewerId = optionalJwtSubjectExtractor.extractSubject(authorizationHeader);
    List<PostResponse> posts = userProfileService.getUserPosts(username, viewerId, page, pageSize);
    return Response.ok(posts).build();
  }

  /**
   * Paginated list of users following {@code username}. The viewer (taken from the Authorization
   * header if present) controls the per-row isFollowing flag.
   */
  @GET
  @Path("/{username}/followers")
  public Response getUserFollowers(
      @PathParam("username") String username,
      @HeaderParam("Authorization") String authorizationHeader,
      @QueryParam("page") @DefaultValue("0") int page,
      @QueryParam("pageSize") @DefaultValue("30") int pageSize) {
    String viewerId = optionalJwtSubjectExtractor.extractSubject(authorizationHeader);
    List<UserSearchResultDto> followers =
        userProfileService.getUserFollowers(username, viewerId, page, pageSize);
    return Response.ok(followers).build();
  }

  /** Paginated list of users that {@code username} follows. */
  @GET
  @Path("/{username}/following")
  public Response getUserFollowing(
      @PathParam("username") String username,
      @HeaderParam("Authorization") String authorizationHeader,
      @QueryParam("page") @DefaultValue("0") int page,
      @QueryParam("pageSize") @DefaultValue("30") int pageSize) {
    String viewerId = optionalJwtSubjectExtractor.extractSubject(authorizationHeader);
    List<UserSearchResultDto> following =
        userProfileService.getUserFollowing(username, viewerId, page, pageSize);
    return Response.ok(following).build();
  }

  @POST
  @Path("/{targetUserId}/follow")
  @Authenticated
  public Response followUser(@PathParam("targetUserId") String targetUserId) {
    String userId = jwt.getSubject();
    FollowActionResponse response = followUserUseCase.follow(userId, targetUserId);
    return Response.ok(response).build();
  }

  @DELETE
  @Path("/{targetUserId}/follow")
  @Authenticated
  public Response unfollowUser(@PathParam("targetUserId") String targetUserId) {
    String userId = jwt.getSubject();
    FollowActionResponse response = unfollowUserUseCase.unfollow(userId, targetUserId);
    return Response.ok(response).build();
  }

  @GET
  @Path("/search")
  @Authenticated
  public Response searchUsers(
      @QueryParam("q") String q,
      @QueryParam("page") Integer page,
      @QueryParam("pageSize") Integer pageSize) {
    String userId = jwt.getSubject();
    int effectivePage = page != null ? page : 0;
    int effectivePageSize = pageSize != null ? pageSize : 20;
    UserSearchResponseDto response =
        searchUsersUseCase.searchUsers(userId, q, effectivePage, effectivePageSize);
    return Response.ok(response).build();
  }
}
