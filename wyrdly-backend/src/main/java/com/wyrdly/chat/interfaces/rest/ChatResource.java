package com.wyrdly.chat.interfaces.rest;

import com.wyrdly.chat.application.dto.ChatHistoryPage;
import com.wyrdly.chat.application.usecase.GetChatHistoryUseCase;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.logging.Logger;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/api/chat")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class ChatResource {

  private static final Logger LOGGER = Logger.getLogger(ChatResource.class.getName());

  @Inject GetChatHistoryUseCase getChatHistoryUseCase;

  @Inject JsonWebToken jwt;

  @GET
  @Path("/{recipientId}/history")
  @Authenticated
  public Response getChatHistory(
      @PathParam("recipientId") String recipientId,
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("pageSize") @DefaultValue("50") int pageSize) {
    String userId = jwt.getName();
    if (userId == null || userId.isEmpty()) {
      return Response.status(Response.Status.UNAUTHORIZED)
          .entity(new ErrorResponse("Unable to identify user"))
          .build();
    }

    if (pageSize < 1 || pageSize > 100) {
      pageSize = 50;
    }
    if (page < 1) {
      page = 1;
    }

    // ✅ Fix #3: Remover catch genérico
    // ExceptionMappers manejan excepciones específicas automáticamente
    ChatHistoryPage history = getChatHistoryUseCase.execute(userId, recipientId, page, pageSize);

    LOGGER.info(
        "Retrieved chat history for user: " + userId + " with " + recipientId + " page: " + page);
    return Response.ok(history).build();
  }

  public static class ErrorResponse {
    public String error;

    public ErrorResponse(String error) {
      this.error = error;
    }

    public String getError() {
      return error;
    }

    public void setError(String error) {
      this.error = error;
    }
  }
}
