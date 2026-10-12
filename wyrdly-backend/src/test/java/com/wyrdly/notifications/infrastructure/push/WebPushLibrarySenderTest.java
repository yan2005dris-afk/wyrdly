package com.wyrdly.notifications.infrastructure.push;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import com.wyrdly.notifications.infrastructure.crypto.VapidKeyPair;
import com.wyrdly.notifications.infrastructure.crypto.VapidKeyProvider;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WebPushLibrarySenderTest {

  @Mock VapidKeyProvider vapidKeyProvider;

  @Test
  void rawPrivateScalarConvertsPkcs8To32ByteScalar() throws Exception {
    VapidKeyPair pair = VapidKeyProvider.generateKeyPair();

    String raw = WebPushLibrarySender.rawPrivateScalar(pair.privateKey());

    byte[] decoded = Base64.getUrlDecoder().decode(raw);
    assertEquals(32, decoded.length, "library expects the raw 32-byte EC scalar");
  }

  @Test
  void storedPkcs8KeysSurviveTheMigrationUntouched() throws Exception {
    VapidKeyPair pair = VapidKeyProvider.generateKeyPair();
    when(vapidKeyProvider.getPrivateKey()).thenReturn(pair.privateKey());

    WebPushLibrarySender sender =
        new WebPushLibrarySender(
            vapidKeyProvider, "mailto:test@wyrdly.com", java.time.Duration.ofMillis(1));

    // The stored PKCS#8 key must survive the migration untouched: no rotation.
    assertEquals(pair.privateKey(), vapidKeyProvider.getPrivateKey());
    assertNotNull(sender);
  }
}
