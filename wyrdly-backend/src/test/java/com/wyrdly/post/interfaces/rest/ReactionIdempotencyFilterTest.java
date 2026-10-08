package com.wyrdly.post.interfaces.rest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReactionIdempotencyFilterTest {

  private ReactionIdempotencyFilter filter;
  private RedisDataSource redis;
  private ValueCommands<String, String> values;
  private ObjectMapper objectMapper;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    filter = new ReactionIdempotencyFilter();
    redis = mock(RedisDataSource.class);
    values = mock(ValueCommands.class);
    when(redis.value(String.class)).thenReturn(values);

    objectMapper = new ObjectMapper();
    filter.redis = redis;
    filter.objectMapper = objectMapper;
    filter.quarkusProfile = "prod";
  }

  private ContainerRequestContext mockRequest(String path, String userId, String bodyJson) {
    ContainerRequestContext req = mock(ContainerRequestContext.class);
    UriInfo uriInfo = mock(UriInfo.class);
    when(uriInfo.getPath()).thenReturn(path);
    when(req.getUriInfo()).thenReturn(uriInfo);

    SecurityContext sec = mock(SecurityContext.class);
    Principal principal = () -> userId;
    when(sec.getUserPrincipal()).thenReturn(principal);
    when(req.getSecurityContext()).thenReturn(sec);

    if (bodyJson != null) {
      when(req.hasEntity()).thenReturn(true);
      when(req.getEntityStream())
          .thenAnswer(inv -> new ByteArrayInputStream(bodyJson.getBytes(StandardCharsets.UTF_8)));
    } else {
      when(req.hasEntity()).thenReturn(false);
    }

    return req;
  }

  @Test
  void filter_AbortsWithCachedResponse_WhenSameReactionTypeCached() throws IOException {
    ContainerRequestContext req =
        mockRequest("/api/posts/pst_1/react", "usr_1", "{\"type\":\"LIKE\"}");

    when(values.get("react:usr_1:pst_1:LIKE"))
        .thenReturn("{\"status\":\"ADDED\",\"reactionType\":\"LIKE\"}");

    filter.filter(req);

    ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
    verify(req).abortWith(captor.capture());
    Response response = captor.getValue();
    assertEquals(200, response.getStatus());
    assertEquals("true", response.getHeaderString("X-Cached-Response"));
  }

  @Test
  void filter_DoesNotAbort_WhenDifferentReactionTypeRequested() throws IOException {
    ContainerRequestContext req =
        mockRequest("/api/posts/pst_1/react", "usr_1", "{\"type\":\"LOVE\"}");

    when(values.get("react:usr_1:pst_1:LOVE")).thenReturn(null);

    filter.filter(req);

    verify(req, never()).abortWith(any());
    verify(req).setProperty("com.wyrdly.reaction.type", "LOVE");
  }

  @Test
  void filterResponse_CachesByType_AndInvalidatesOtherTypes() throws IOException {
    ContainerRequestContext req =
        mockRequest("/api/posts/pst_1/react", "usr_1", "{\"type\":\"LOVE\"}");
    when(req.getProperty("com.wyrdly.reaction.type")).thenReturn("LOVE");

    ContainerResponseContext resp = mock(ContainerResponseContext.class);
    when(resp.getStatus()).thenReturn(200);
    when(resp.getEntity())
        .thenReturn(java.util.Map.of("status", "UPDATED", "reactionType", "LOVE"));

    filter.filter(req, resp);

    verify(values).setex(eq("react:usr_1:pst_1:LOVE"), eq(1L), contains("LOVE"));
    verify(redis).execute("DEL", "react:usr_1:pst_1:LIKE");
    verify(redis).execute("DEL", "react:usr_1:pst_1:CELEBRATE");
  }
}
