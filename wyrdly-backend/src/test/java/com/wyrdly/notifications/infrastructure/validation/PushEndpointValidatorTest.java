package com.wyrdly.notifications.infrastructure.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.wyrdly.notifications.domain.exception.InvalidSubscriptionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PushEndpointValidatorTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://fcm.googleapis.com/fcm/send/abc",
        "https://updates.push.services.mozilla.com/wpush/v2/abc",
        "https://push.example.com/endpoint/xyz",
        "https://fcm.googleapis.com:443/fcm/send/abc",
        "https://a.b.c.d.e.f.g.example.com/x",
      })
  void acceptsValidPublicHttpsEndpoints(String endpoint) {
    assertDoesNotThrow(() -> PushEndpointValidator.validate(endpoint));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://fcm.googleapis.com/fcm/send/abc",
        "ftp://push.example.com/abc",
        "wss://push.example.com/abc",
        "HTTPS-without-colon",
      })
  void rejectsNonHttpsOrUnparseable(String endpoint) {
    assertThrows(
        InvalidSubscriptionException.class,
        () -> PushEndpointValidator.validate(endpoint));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://localhost:9999/mock-push",
        "https://LOCALHOST/foo",
        "https://api.localhost/foo",
        "https://127.0.0.1/foo",
        "https://192.168.0.1/foo",
        "https://10.0.0.1/foo",
        "https://[2001:db8::1]/foo",
        "https://example.localhost/foo",
        "https://router.local/foo",
        "https://api.internal/foo",
        "https://example/",
      })
  void rejectsLoopbackOrReservedHosts(String endpoint) {
    assertThrows(
        InvalidSubscriptionException.class,
        () -> PushEndpointValidator.validate(endpoint));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://user:pass@fcm.googleapis.com/foo",
        "https://fcm.googleapis.com/foo#frag",
      })
  void rejectsUserInfoOrFragment(String endpoint) {
    assertThrows(
        InvalidSubscriptionException.class,
        () -> PushEndpointValidator.validate(endpoint));
  }

  @Test
  void rejectsMalformedUri() {
    assertThrows(
        InvalidSubscriptionException.class,
        () -> PushEndpointValidator.validate("https://exa mple.com/foo"));
  }

  @Test
  void rejectsBlankOrNull() {
    assertThrows(
        InvalidSubscriptionException.class,
        () -> PushEndpointValidator.validate(null));
    assertThrows(
        InvalidSubscriptionException.class,
        () -> PushEndpointValidator.validate(""));
    assertThrows(
        InvalidSubscriptionException.class,
        () -> PushEndpointValidator.validate("   "));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://-bad.example.com/foo",
        "https://bad-.example.com/foo",
        "https://ex ample.com/foo",
        "https://example.123/foo",
      })
  void rejectsMalformedLabels(String endpoint) {
    assertThrows(
        InvalidSubscriptionException.class,
        () -> PushEndpointValidator.validate(endpoint));
  }

  @Test
  void rejectsLabelLongerThanSixtyThreeChars() {
    String longLabel = "a".repeat(64);
    String endpoint = "https://prefix." + longLabel + ".com/foo";
    assertThrows(
        InvalidSubscriptionException.class,
        () -> PushEndpointValidator.validate(endpoint));
  }
}