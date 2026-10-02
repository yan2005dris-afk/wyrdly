package com.wyrdly.chat.interfaces.rest;

import com.wyrdly.chat.domain.exception.InvalidMessageException;
import com.wyrdly.chat.domain.exception.UsersNotFollowingException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.Map;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

@ApplicationScoped
public class ChatExceptionMappers {

  @ServerExceptionMapper
  public Response handleInvalidMessage(InvalidMessageException ex) {
    return buildResponse(Response.Status.BAD_REQUEST, ex.getMessage());
  }

  @ServerExceptionMapper
  public Response handleUsersNotFollowing(UsersNotFollowingException ex) {
    return buildResponse(Response.Status.FORBIDDEN, ex.getMessage());
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
