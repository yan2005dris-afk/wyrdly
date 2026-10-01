package com.yaga.chat.interfaces.rest;

import com.yaga.chat.domain.exception.ChatSessionNotFoundException;
import com.yaga.chat.domain.exception.InvalidMessageException;
import com.yaga.chat.domain.exception.UsersNotFollowingException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.HashMap;
import java.util.Map;

@Provider
public class ChatExceptionMappers {

  @Provider
  public static class InvalidMessageExceptionMapper
      implements ExceptionMapper<InvalidMessageException> {
    @Override
    public Response toResponse(InvalidMessageException exception) {
      Map<String, String> error = new HashMap<>();
      error.put("error", exception.getMessage());
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(error)
          .type(MediaType.APPLICATION_JSON)
          .build();
    }
  }

  @Provider
  public static class ChatSessionNotFoundExceptionMapper
      implements ExceptionMapper<ChatSessionNotFoundException> {
    @Override
    public Response toResponse(ChatSessionNotFoundException exception) {
      Map<String, String> error = new HashMap<>();
      error.put("error", exception.getMessage());
      return Response.status(Response.Status.NOT_FOUND)
          .entity(error)
          .type(MediaType.APPLICATION_JSON)
          .build();
    }
  }

  @Provider
  public static class UsersNotFollowingExceptionMapper
      implements ExceptionMapper<UsersNotFollowingException> {
    @Override
    public Response toResponse(UsersNotFollowingException exception) {
      Map<String, String> error = new HashMap<>();
      error.put("error", exception.getMessage());
      return Response.status(Response.Status.FORBIDDEN)
          .entity(error)
          .type(MediaType.APPLICATION_JSON)
          .build();
    }
  }
}
