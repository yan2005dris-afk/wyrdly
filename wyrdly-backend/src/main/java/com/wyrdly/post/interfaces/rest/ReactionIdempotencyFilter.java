package com.wyrdly.post.interfaces.rest;

import io.quarkus.logging.Log;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;

/**
 * Short-window Redis dedup for {@code POST /api/posts/{postId}/react}. If the same user submits the
 * same reaction for the same post within {@value #TTL_SECONDS} seconds the cached body is replayed
 * verbatim, so a double click does not produce two toggles. Runs after authentication (priority
 * {@code AUTHENTICATION + 100}) and before {@link ReactionRateLimitFilter}.
 *
 * <p>Redis is treated as a best-effort optimization: any cache error degrades gracefully to "no
 * dedup" so the toggle request still reaches the repository. The repository-side query is itself
 * atomic, so a Redis outage cannot corrupt reaction data.
 */
@Provider
@Priority(Priorities.AUTHENTICATION + 100)
public class ReactionIdempotencyFilter implements ContainerRequestFilter, ContainerResponseFilter {

  /** Cache TTL in seconds. Keeps a single in-flight toggle deduplicated. */
  static final long TTL_SECONDS = 1L;

  private static final String PATH_PREFIX = "/api/posts/";
  private static final String SUFFIX = "/react";

  @Inject RedisDataSource redis;

  private ValueCommands<String, String> values;

  private ValueCommands<String, String> values() {
    ValueCommands<String, String> v = values;
    if (v == null) {
      v = redis.value(String.class);
      values = v;
    }
    return v;
  }

  @Override
  public void filter(ContainerRequestContext req) throws IOException {
    String path = req.getUriInfo().getPath();
    if (!isReactionEndpoint(path)) {
      return;
    }
    String userId = currentUserId(req);
    String postId = extractPostId(path);
    if (userId == null || postId == null) {
      return;
    }

    String key = "react:" + userId + ":" + postId;
    try {
      String cached = values().get(key);
      if (cached != null) {
        req.abortWith(Response.ok(cached).type("application/json").build());
      }
    } catch (Exception e) {
      Log.warnf(e, "Idempotency cache lookup failed for key=%s — falling back to repository", key);
    }
  }

  @Override
  public void filter(ContainerRequestContext req, ContainerResponseContext resp)
      throws IOException {
    if (resp.getStatus() != 200) {
      return;
    }
    String path = req.getUriInfo().getPath();
    if (!isReactionEndpoint(path)) {
      return;
    }
    String userId = currentUserId(req);
    String postId = extractPostId(path);
    if (userId == null || postId == null) {
      return;
    }

    Object entity = resp.getEntity();
    if (entity == null) {
      return;
    }
    String key = "react:" + userId + ":" + postId;
    try {
      values().setex(key, TTL_SECONDS, entity.toString());
    } catch (Exception e) {
      Log.warnf(e, "Idempotency cache write failed for key=%s — toggle still completed", key);
    }
  }

  private static boolean isReactionEndpoint(String path) {
    return path != null && path.startsWith(PATH_PREFIX) && path.endsWith(SUFFIX);
  }

  /** Path layout: {@code api/posts/{postId}/react} (no leading slash from {@code UriInfo}). */
  private static String extractPostId(String path) {
    if (path == null) {
      return null;
    }
    int start = PATH_PREFIX.length();
    int end = path.indexOf(SUFFIX, start);
    if (end < 0) {
      return null;
    }
    String postId = path.substring(start, end);
    return postId.isBlank() ? null : postId;
  }

  private static String currentUserId(ContainerRequestContext req) {
    var sec = req.getSecurityContext();
    if (sec == null || sec.getUserPrincipal() == null) {
      return null;
    }
    return sec.getUserPrincipal().getName();
  }
}
