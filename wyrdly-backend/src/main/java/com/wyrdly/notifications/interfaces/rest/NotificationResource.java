package com.wyrdly.notifications.interfaces.rest;

import com.wyrdly.notifications.application.dto.NotificationDto;
import com.wyrdly.notifications.application.dto.NotificationListResponseDto;
import com.wyrdly.notifications.application.dto.SubscribeRequestDto;
import com.wyrdly.notifications.application.dto.SubscribeResponseDto;
import com.wyrdly.notifications.application.dto.VapidPublicKeyResponseDto;
import com.wyrdly.notifications.application.port.NotificationBroadcasterPort;
import com.wyrdly.notifications.application.usecase.GetNotificationsForUserUseCase;
import com.wyrdly.notifications.application.usecase.MarkAllNotificationsReadUseCase;
import com.wyrdly.notifications.application.usecase.MarkNotificationReadUseCase;
import com.wyrdly.notifications.application.usecase.SubscribeToPushUseCase;
import com.wyrdly.notifications.application.usecase.UnsubscribeFromPushUseCase;
import com.wyrdly.notifications.infrastructure.crypto.VapidKeyProvider;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.resteasy.reactive.RestStreamElementType;

/**
 * REST surface for the notifications bounded context.
 *
 * <p>Web Push subscription lifecycle:
 *
 * <ul>
 *   <li>{@code GET /api/notifications/vapid-public-key} — public.
 *   <li>{@code POST /api/notifications/subscribe} — JWT-protected.
 *   <li>{@code DELETE /api/notifications/subscribe} — JWT-protected.
 * </ul>
 *
 * <p>In-app feed:
 *
 * <ul>
 *   <li>{@code GET /api/notifications?page=0&pageSize=20} — paginated feed for the caller.
 *   <li>{@code POST /api/notifications/{id}/read} — mark a single notification as read.
 *   <li>{@code POST /api/notifications/mark-all-read} — mark all of the caller's as read.
 * </ul>
 */
@Path("/api/notifications")
@Produces(MediaType.APPLICATION_JSON)
public class NotificationResource {

  private final VapidKeyProvider vapidKeyProvider;
  private final SubscribeToPushUseCase subscribeUseCase;
  private final UnsubscribeFromPushUseCase unsubscribeUseCase;
  private final GetNotificationsForUserUseCase getNotificationsUseCase;
  private final MarkNotificationReadUseCase markReadUseCase;
  private final MarkAllNotificationsReadUseCase markAllReadUseCase;
  private final NotificationBroadcasterPort broadcaster;
  private final JsonWebToken jwt;

  @Inject
  public NotificationResource(
      VapidKeyProvider vapidKeyProvider,
      SubscribeToPushUseCase subscribeUseCase,
      UnsubscribeFromPushUseCase unsubscribeUseCase,
      GetNotificationsForUserUseCase getNotificationsUseCase,
      MarkNotificationReadUseCase markReadUseCase,
      MarkAllNotificationsReadUseCase markAllReadUseCase,
      NotificationBroadcasterPort broadcaster,
      JsonWebToken jwt) {
    this.vapidKeyProvider = vapidKeyProvider;
    this.subscribeUseCase = subscribeUseCase;
    this.unsubscribeUseCase = unsubscribeUseCase;
    this.getNotificationsUseCase = getNotificationsUseCase;
    this.markReadUseCase = markReadUseCase;
    this.markAllReadUseCase = markAllReadUseCase;
    this.broadcaster = broadcaster;
    this.jwt = jwt;
  }

  // ----- Web Push subscription lifecycle -----

  @GET
  @Path("/vapid-public-key")
  public Response getVapidPublicKey() {
    return Response.ok(new VapidPublicKeyResponseDto(vapidKeyProvider.getPublicKey())).build();
  }

  @POST
  @Path("/subscribe")
  @Consumes(MediaType.APPLICATION_JSON)
  @Authenticated
  public Response subscribe(@Valid SubscribeRequestDto request) {
    String userId = currentUserId();
    SubscribeResponseDto response = subscribeUseCase.subscribe(userId, request);
    return Response.ok(response).build();
  }

  @DELETE
  @Path("/subscribe")
  @Authenticated
  public Response unsubscribe() {
    String userId = currentUserId();
    SubscribeResponseDto response = unsubscribeUseCase.unsubscribe(userId);
    return Response.ok(response).build();
  }

  // ----- In-app feed -----

  @GET
  @Authenticated
  public Response listForCurrentUser(
      @QueryParam("page") @DefaultValue("0") int page,
      @QueryParam("pageSize") @DefaultValue("20") int pageSize) {
    String userId = currentUserId();
    NotificationListResponseDto body = getNotificationsUseCase.execute(userId, page, pageSize);
    return Response.ok(body).build();
  }

  @POST
  @Path("/{id}/read")
  @Authenticated
  public Response markAsRead(@PathParam("id") String notificationId) {
    String userId = currentUserId();
    markReadUseCase.execute(userId, notificationId);
    return Response.noContent().build();
  }

  @POST
  @Path("/mark-all-read")
  @Authenticated
  public Response markAllAsRead() {
    String userId = currentUserId();
    long updated = markAllReadUseCase.execute(userId);
    return Response.ok(java.util.Map.of("updated", updated)).build();
  }

  @GET
  @Path("/stream")
  @Produces(MediaType.SERVER_SENT_EVENTS)
  @RestStreamElementType(MediaType.APPLICATION_JSON)
  @Authenticated
  public Multi<NotificationDto> stream() {
    String userId = currentUserId();
    return broadcaster.subscribe(userId);
  }

  private String currentUserId() {
    if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
      // @Authenticated would normally reject this before we get here; defensive guard.
      throw new IllegalStateException("Authenticated request is missing subject claim");
    }
    return jwt.getSubject();
  }
}
