package com.wyrdly.notifications.interfaces.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wyrdly.notifications.application.dto.NotificationDto;
import com.wyrdly.notifications.application.dto.NotificationListResponseDto;
import com.wyrdly.notifications.application.dto.SubscribeRequestDto;
import com.wyrdly.notifications.application.dto.SubscribeResponseDto;
import com.wyrdly.notifications.application.usecase.GetNotificationsForUserUseCase;
import com.wyrdly.notifications.application.usecase.MarkAllNotificationsReadUseCase;
import com.wyrdly.notifications.application.usecase.MarkNotificationReadUseCase;
import com.wyrdly.notifications.application.usecase.SubscribeToPushUseCase;
import com.wyrdly.notifications.application.usecase.UnsubscribeFromPushUseCase;
import com.wyrdly.notifications.infrastructure.crypto.VapidKeyProvider;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.Set;
import org.eclipse.microprofile.jwt.Claims;
import org.junit.jupiter.api.Test;

@QuarkusTest
class NotificationResourceTest {

  @InjectMock VapidKeyProvider vapidKeyProvider;
  @InjectMock SubscribeToPushUseCase subscribeUseCase;
  @InjectMock UnsubscribeFromPushUseCase unsubscribeUseCase;
  @InjectMock GetNotificationsForUserUseCase getNotificationsUseCase;
  @InjectMock MarkNotificationReadUseCase markReadUseCase;
  @InjectMock MarkAllNotificationsReadUseCase markAllReadUseCase;

  @Test
  void getVapidPublicKeyReturnsTheServerKey() {
    // Random-looking placeholder, NOT a real VAPID public key.
    String mockedToken = "mocked-public-key-token-for-test";
    when(vapidKeyProvider.getPublicKey()).thenReturn(mockedToken);

    given()
        .when()
        .get("/api/notifications/vapid-public-key")
        .then()
        .statusCode(200)
        .body("publicKey", equalTo(mockedToken));
  }

  @Test
  void subscribeRequiresAuth() {
    given()
        .contentType(ContentType.JSON)
        .body("{\"endpoint\":\"https://x\",\"keys\":{\"p256dh\":\"Ba\",\"auth\":\"tb==\"}}")
        .when()
        .post("/api/notifications/subscribe")
        .then()
        .statusCode(401);
  }

  @Test
  void subscribeHappyPath() {
    String userId = "usr_abc";
    when(subscribeUseCase.subscribe(eq(userId), any(SubscribeRequestDto.class)))
        .thenReturn(SubscribeResponseDto.subscribed());

    given()
        .auth()
        .oauth2(jwtFor(userId))
        .contentType(ContentType.JSON)
        .body(
            "{\"endpoint\":\"https://fcm.googleapis.com/fcm/send/abc\","
                + "\"keys\":{\"p256dh\":\"mocked-p256dh-for-test\",\"auth\":\"mocked-auth-for-test\"}}")
        .when()
        .post("/api/notifications/subscribe")
        .then()
        .statusCode(200)
        .body("status", equalTo("SUBSCRIBED"));

    verify(subscribeUseCase, times(1)).subscribe(eq(userId), any(SubscribeRequestDto.class));
  }

  @Test
  void subscribeRejectsMissingEndpoint() {
    given()
        .auth()
        .oauth2(jwtFor("usr_abc"))
        .contentType(ContentType.JSON)
        .body("{\"keys\":{\"p256dh\":\"Ba\",\"auth\":\"tb\"}}")
        .when()
        .post("/api/notifications/subscribe")
        .then()
        .statusCode(400);
  }

  @Test
  void subscribeRejectsMissingP256dh() {
    given()
        .auth()
        .oauth2(jwtFor("usr_abc"))
        .contentType(ContentType.JSON)
        .body("{\"endpoint\":\"https://example.com/push\"," + "\"keys\":{\"auth\":\"tb\"}}")
        .when()
        .post("/api/notifications/subscribe")
        .then()
        .statusCode(400);
  }

  @Test
  void subscribeRejectsNonHttpsEndpoint() {
    given()
        .auth()
        .oauth2(jwtFor("usr_abc"))
        .contentType(ContentType.JSON)
        .body(
            "{\"endpoint\":\"http://insecure.example/push\","
                + "\"keys\":{\"p256dh\":\"Ba\",\"auth\":\"tb\"}}")
        .when()
        .post("/api/notifications/subscribe")
        .then()
        .statusCode(400);
  }

  @Test
  void unsubscribeHappyPath() {
    String userId = "usr_abc";
    when(unsubscribeUseCase.unsubscribe(userId)).thenReturn(SubscribeResponseDto.unsubscribed());

    given()
        .auth()
        .oauth2(jwtFor(userId))
        .when()
        .delete("/api/notifications/subscribe")
        .then()
        .statusCode(200)
        .body("status", equalTo("UNSUBSCRIBED"));

    verify(unsubscribeUseCase, times(1)).unsubscribe(userId);
  }

  @Test
  void unsubscribeRequiresAuth() {
    given().when().delete("/api/notifications/subscribe").then().statusCode(401);
  }

  // ----- In-app feed -----

  @Test
  void listReturnsPagedResponseForAuthenticatedUser() {
    String userId = "usr_alice";
    NotificationDto dto =
        new NotificationDto(
            "ntf_abc",
            "GRAPH_FOLLOW",
            "Nuevo seguidor",
            "Bob comenzó a seguirte",
            "/feed",
            null,
            false,
            java.time.Instant.parse("2026-01-15T10:00:00Z"),
            new NotificationDto.ActorDto("usr_bob", "bob", "Bob Marley", "", ""));
    NotificationListResponseDto body =
        new NotificationListResponseDto(java.util.List.of(dto), 1L, 0, 20, 1L);
    when(getNotificationsUseCase.execute(userId, 0, 20)).thenReturn(body);

    given()
        .auth()
        .oauth2(jwtFor(userId))
        .when()
        .get("/api/notifications")
        .then()
        .statusCode(200)
        .body("unreadCount", equalTo(1))
        .body("page", equalTo(0))
        .body("notifications[0].id", equalTo("ntf_abc"))
        .body("notifications[0].type", equalTo("GRAPH_FOLLOW"))
        .body("notifications[0].actor.username", equalTo("bob"));

    verify(getNotificationsUseCase).execute(userId, 0, 20);
  }

  @Test
  void listRequiresAuth() {
    given().when().get("/api/notifications").then().statusCode(401);
  }

  @Test
  void markAsReadReturnsNoContent() {
    String userId = "usr_alice";

    given()
        .auth()
        .oauth2(jwtFor(userId))
        .when()
        .post("/api/notifications/ntf_abc/read")
        .then()
        .statusCode(204);

    verify(markReadUseCase).execute(userId, "ntf_abc");
  }

  @Test
  void markAsReadRequiresAuth() {
    given().when().post("/api/notifications/ntf_abc/read").then().statusCode(401);
  }

  @Test
  void markAllAsReadReturnsUpdatedCount() {
    String userId = "usr_alice";
    when(markAllReadUseCase.execute(userId)).thenReturn(5L);

    given()
        .auth()
        .oauth2(jwtFor(userId))
        .when()
        .post("/api/notifications/mark-all-read")
        .then()
        .statusCode(200)
        .body("updated", equalTo(5));

    verify(markAllReadUseCase).execute(userId);
  }

  @Test
  void markAllAsReadRequiresAuth() {
    given().when().post("/api/notifications/mark-all-read").then().statusCode(401);
  }

  // ---- helpers ------------------------------------------------------------

  /** Mints a JWT whose subject claim equals {@code userId}; required by @Authenticated. */
  private static String jwtFor(String userId) {
    return io.smallrye.jwt.build.Jwt.issuer("https://wyrdly.com/issuer")
        .upn("testuser")
        .subject(userId)
        .groups(Set.of("User"))
        .claim(Claims.email.name(), "test@wyrdly.com")
        .expiresIn(java.time.Duration.ofMinutes(5))
        .sign();
  }
}
