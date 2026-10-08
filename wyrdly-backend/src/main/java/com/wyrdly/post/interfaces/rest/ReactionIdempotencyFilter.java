package com.wyrdly.post.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

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
  static final String REACTION_TYPE_PROPERTY = "com.wyrdly.reaction.type";

  @Inject RedisDataSource redis;
  @Inject ObjectMapper objectMapper;

  @Inject
  @ConfigProperty(name = "quarkus.profile", defaultValue = "prod")
  String quarkusProfile;

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
    // Skip cache in test profile
    if ("test".equals(quarkusProfile)) {
      return;
    }
    String path = normalizePath(req.getUriInfo().getPath());
    if (!isReactionEndpoint(path)) {
      return;
    }
    String userId = currentUserId(req);
    String postId = extractPostId(path);
    if (userId == null || postId == null) {
      return;
    }

    String type = extractReactionType(req);
    if (type == null) {
      return;
    }
    req.setProperty(REACTION_TYPE_PROPERTY, type);

    String key = cacheKey(userId, postId, type);
    try {
      String cached = values().get(key);
      if (cached != null) {
        req.abortWith(Response.ok().entity(cached).header("X-Cached-Response", "true").build());
      }
    } catch (Exception e) {
      Log.warnf(e, "Idempotency cache lookup failed for key=%s — falling back to repository", key);
    }
  }

  @Override
  public void filter(ContainerRequestContext req, ContainerResponseContext resp)
      throws IOException {
    // Skip cache in test profile
    if ("test".equals(quarkusProfile)) {
      return;
    }
    if (resp.getStatus() != 200) {
      return;
    }
    String path = normalizePath(req.getUriInfo().getPath());
    if (!isReactionEndpoint(path)) {
      return;
    }
    String userId = currentUserId(req);
    String postId = extractPostId(path);
    if (userId == null || postId == null) {
      return;
    }

    String type = (String) req.getProperty(REACTION_TYPE_PROPERTY);
    if (type == null) {
      return;
    }

    Object entity = resp.getEntity();
    if (entity == null) {
      return;
    }
    String key = cacheKey(userId, postId, type);
    try {
      String json = objectMapper.writeValueAsString(entity);
      values().setex(key, TTL_SECONDS, json);
      // Invalidate cache for other reaction types for this (userId, postId)
      for (com.wyrdly.post.domain.model.ReactionType rt :
          com.wyrdly.post.domain.model.ReactionType.values()) {
        if (!rt.name().equals(type)) {
          try {
            redis.execute("DEL", cacheKey(userId, postId, rt.name()));
          } catch (Exception ignored) {
          }
        }
      }
    } catch (Exception e) {
      Log.warnf(e, "Idempotency cache write failed for key=%s — toggle still completed", key);
    }
  }

  private String extractReactionType(ContainerRequestContext req) {
    if (!req.hasEntity()) {
      return null;
    }
    try {
      java.io.InputStream stream = req.getEntityStream();
      if (stream == null) {
        return null;
      }
      byte[] body = stream.readAllBytes();
      req.setEntityStream(new java.io.ByteArrayInputStream(body));
      com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(body);
      if (node != null && node.hasNonNull("type")) {
        return node.get("type").asText();
      }
    } catch (Exception e) {
      // Degrade gracefully if body cannot be parsed
    }
    return null;
  }

  private static String cacheKey(String userId, String postId, String type) {
    return "react:" + userId + ":" + postId + ":" + type;
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
    if (sec.getUserPrincipal() instanceof JsonWebToken jwt) {
      String sub = jwt.getSubject();
      if (sub != null && !sub.isBlank()) {
        return sub;
      }
    }
    return sec.getUserPrincipal().getName();
  }
}
