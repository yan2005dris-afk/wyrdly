package com.yaga.user.interfaces.rest;

import com.yaga.user.domain.exception.SelfFollowNotAllowedException;
import com.yaga.user.domain.exception.UserProfileNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.Map;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

@ApplicationScoped
public class UserProfileExceptionMappers {

  @ServerExceptionMapper
  public Response handleUserProfileNotFound(UserProfileNotFoundException ex) {
    return buildResponse(Response.Status.NOT_FOUND, ex.getMessage());
  }

  @ServerExceptionMapper
  public Response handleSelfFollowNotAllowed(SelfFollowNotAllowedException ex) {
    return buildResponse(Response.Status.BAD_REQUEST, ex.getMessage());
  }

  private Response buildResponse(Response.Status status, String message) {
    Map<String, Object> body =
        Map.of(
            "status", status.getStatusCode(),
            "error", status.getReasonPhrase(),
            "message", message != null ? message : "",
            "timestamp", Instant.now().toString());
    return Response.status(status).entity(body).build();
  }
}
