package com.wyrdly.notifications.interfaces.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wyrdly.notifications.application.dto.SubscribeRequestDto;
import com.wyrdly.notifications.application.dto.SubscribeResponseDto;
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

  @Test
  void getVapidPublicKeyReturnsTheServerKey() {
    // Mocked value: NOT a real VAPID key (avoid GitGuardian false positives).
    String mockedKey = "mock-vapid-public-key-for-test-only";
    when(vapidKeyProvider.getPublicKey()).thenReturn(mockedKey);

    given()
        .when()
        .get("/api/notifications/vapid-public-key")
        .then()
        .statusCode(200)
        .body("publicKey", equalTo(mockedKey));
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
                + "\"keys\":{\"p256dh\":\"mock-p256dh-do-not-use\",\"auth\":\"mock-auth-secret\"}}")
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
