package com.wyrdly.notifications.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wyrdly.notifications.application.dto.SubscribeRequestDto;
import com.wyrdly.notifications.application.dto.SubscribeRequestDto.SubscriptionKeysDto;
import com.wyrdly.notifications.application.dto.SubscribeResponseDto;
import com.wyrdly.notifications.application.port.PushSubscriptionRepositoryPort;
import com.wyrdly.notifications.application.usecase.impl.SubscribeToPushUseCaseImpl;
import com.wyrdly.notifications.domain.exception.InvalidSubscriptionException;
import com.wyrdly.notifications.domain.model.PushSubscription;
import com.wyrdly.notifications.infrastructure.crypto.VapidKeyProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SubscribeToPushUseCaseImplTest {

  private PushSubscriptionRepositoryPort repo;
  private SubscribeToPushUseCaseImpl useCase;

  private static final String USER_ID = "usr_abc";

  // Generate a real, valid EC P-256 public key once so the use-case validator
  // (which decodes p256dh and asserts the uncompressed point shape) accepts it.
  private static final String REAL_P256DH;
  private static final String VALID_AUTH = "mock-auth-secret-for-test";

  static {
    try {
      REAL_P256DH = VapidKeyProvider.generateKeyPair().publicKey();
    } catch (Exception e) {
      throw new IllegalStateException("could not seed p256dh for tests", e);
    }
  }

  private static SubscribeRequestDto validRequest() {
    return new SubscribeRequestDto(
        "https://fcm.googleapis.com/fcm/send/dK98s",
        new SubscriptionKeysDto(REAL_P256DH, VALID_AUTH));
  }

  @BeforeEach
  void setUp() {
    repo = org.mockito.Mockito.mock(PushSubscriptionRepositoryPort.class);
    useCase = new SubscribeToPushUseCaseImpl(repo);
  }

  @Test
  void persistsSubscriptionAndReturnsSubscribed() {
    SubscribeRequestDto req = validRequest();
    SubscribeResponseDto response = useCase.subscribe(USER_ID, req);
    assertEquals("SUBSCRIBED", response.status());

    ArgumentCaptor<PushSubscription> captor = ArgumentCaptor.forClass(PushSubscription.class);
    verify(repo, times(1)).save(eq(USER_ID), captor.capture());
    PushSubscription saved = captor.getValue();
    assertEquals(req.endpoint(), saved.endpoint());
    assertEquals(req.keys().p256dh(), saved.p256dh());
    assertEquals(req.keys().auth(), saved.auth());
  }

  @Test
  void trimsEndpointAndKeysBeforePersisting() throws Exception {
    String p256dh = VapidKeyProvider.generateKeyPair().publicKey();
    SubscribeRequestDto withWhitespace =
        new SubscribeRequestDto(
            "  https://fcm.googleapis.com/fcm/send/abc  ",
            new SubscriptionKeysDto("  " + p256dh + "  ", "  " + VALID_AUTH + "  "));
    useCase.subscribe(USER_ID, withWhitespace);

    ArgumentCaptor<PushSubscription> captor = ArgumentCaptor.forClass(PushSubscription.class);
    verify(repo).save(eq(USER_ID), captor.capture());
    PushSubscription saved = captor.getValue();
    assertEquals("https://fcm.googleapis.com/fcm/send/abc", saved.endpoint());
    assertEquals(p256dh, saved.p256dh());
    assertEquals(VALID_AUTH, saved.auth());
  }

  @Test
  void rejectsBlankEndpoint() {
    SubscribeRequestDto bad =
        new SubscribeRequestDto("   ", new SubscriptionKeysDto(REAL_P256DH, VALID_AUTH));
    assertThrows(InvalidSubscriptionException.class, () -> useCase.subscribe(USER_ID, bad));
  }

  @Test
  void rejectsMissingKeys() {
    SubscribeRequestDto bad = new SubscribeRequestDto("https://example.com/push", null);
    assertThrows(InvalidSubscriptionException.class, () -> useCase.subscribe(USER_ID, bad));
  }

  @Test
  void rejectsBlankP256dh() {
    SubscribeRequestDto bad =
        new SubscribeRequestDto(
            "https://example.com/push", new SubscriptionKeysDto("", VALID_AUTH));
    assertThrows(InvalidSubscriptionException.class, () -> useCase.subscribe(USER_ID, bad));
  }

  @Test
  void rejectsBlankAuth() {
    SubscribeRequestDto bad =
        new SubscribeRequestDto(
            "https://example.com/push", new SubscriptionKeysDto(REAL_P256DH, "  "));
    assertThrows(InvalidSubscriptionException.class, () -> useCase.subscribe(USER_ID, bad));
  }

  @Test
  void rejectsMalformedP256dh() {
    // Valid base64/base64url but decodes to bytes that are not 65 bytes / 0x04 prefix.
    SubscribeRequestDto bad =
        new SubscribeRequestDto(
            "https://example.com/push", new SubscriptionKeysDto("aGVsbG8=", VALID_AUTH));
    assertThrows(InvalidSubscriptionException.class, () -> useCase.subscribe(USER_ID, bad));
  }

  @Test
  void rejectsNonBase64UrlP256dh() {
    SubscribeRequestDto bad =
        new SubscribeRequestDto(
            "https://example.com/push", new SubscriptionKeysDto("$$$$", VALID_AUTH));
    assertThrows(InvalidSubscriptionException.class, () -> useCase.subscribe(USER_ID, bad));
  }

  @Test
  void subscribesAreIdempotentWithTheSameUser() {
    when(repo.findByUserId(USER_ID)).thenReturn(null);
    useCase.subscribe(USER_ID, validRequest());
    useCase.subscribe(USER_ID, validRequest());
    verify(repo, times(2)).save(eq(USER_ID), any(PushSubscription.class));
  }

  @Test
  void rejectsNullRequest() {
    assertThrows(InvalidSubscriptionException.class, () -> useCase.subscribe(USER_ID, null));
  }
}
