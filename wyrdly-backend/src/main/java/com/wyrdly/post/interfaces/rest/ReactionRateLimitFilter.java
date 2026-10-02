package com.wyrdly.post.interfaces.rest;

import io.quarkus.logging.Log;
import io.quarkus.redis.datasource.RedisDataSource;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Per-user rate limit for {@code POST /api/posts/{postId}/react}. Uses a Redis {@code INCR} counter
 * with a fixed one-minute window: at most {@value #CAPACITY} reactions per user per minute. Returns
 * {@code 429 Too Many Requests} with {@code Retry-After} when the user exceeds the quota.
 *
 * <p>Runs after {@link ReactionIdempotencyFilter} (priority {@code AUTHENTICATION + 200}) so
 * idempotency replays don't consume budget. Redis outages fail open: a missing cache layer degrades
 * gracefully to "no rate limit" rather than refusing all traffic.
 */
@Provider
@Priority(Priorities.AUTHENTICATION + 200)
public class ReactionRateLimitFilter implements ContainerRequestFilter {

  /** Maximum reactions per user per window. */
  static final long CAPACITY = 30L;

  /** Window length in seconds. */
  static final long WINDOW_SECONDS = 60L;

  private static final String PATH_PREFIX = "/api/posts/";
  private static final String SUFFIX = "/react";

  @Inject RedisDataSource redis;

  @Override
  public void filter(ContainerRequestContext req) throws IOException {
    String path = normalizePath(req.getUriInfo().getPath());
    if (!isReactionEndpoint(path)) {
      return;
    }
    String userId = currentUserId(req);
    if (userId == null) {
      return;
    }
    String key = "ratelimit:react:" + userId;

    long count;
    try {
      count = redis.execute("INCR", key).toLong();
      if (count == 1L) {
        // First request in the window — arm the TTL.
        redis.execute("EXPIRE", key, Long.toString(WINDOW_SECONDS));
      }
    } catch (Exception e) {
      Log.warnf(e, "Rate-limit cache unavailable for key=%s — failing open (request allowed)", key);
      return;
    }

    if (count > CAPACITY) {
      req.abortWith(
          Response.status(429)
              .header("Retry-After", Long.toString(WINDOW_SECONDS))
              .entity("{\"code\":\"RATE_LIMITED\"}")
              .type("application/json")
              .build());
    }
  }

  private static String normalizePath(String path) {
    if (path == null) {
      return null;
    }
    return path.startsWith("/") ? path : "/" + path;
  }

  private static boolean isReactionEndpoint(String path) {
    return path != null && path.startsWith(PATH_PREFIX) && path.endsWith(SUFFIX);
  }

  private static String currentUserId(ContainerRequestContext req) {
    var sec = req.getSecurityContext();
    if (sec == null || sec.getUserPrincipal() == null) {
      return null;
    }
    if (sec.getUserPrincipal() instanceof JsonWebToken jwt) {
      String sub = jwt.getSubject();
      if (sub != null && !sub.isBlank()) {
        return sub;
      }
    }
    return sec.getUserPrincipal().getName();
  }
}
