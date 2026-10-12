package com.wyrdly.notifications.infrastructure.push;

import com.wyrdly.notifications.domain.model.PushSubscription;
import com.wyrdly.notifications.infrastructure.crypto.VapidKeyProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Sends Web Push payloads through the proven {@code nl.martijndwars:web-push} library instead of
 * the former hand-rolled RFC 8291 / RFC 8292 implementation.
 *
 * <p>The stored VAPID private key is PKCS#8 DER (see {@link VapidKeyPair}); the library expects the
 * raw 32-byte EC scalar, so the conversion happens here at service-build time. Stored keys are
 * never rewritten, so no VAPID rotation (and no mass re-subscription) is required by this
 * migration.
 */
@ApplicationScoped
public class WebPushLibrarySender {

  static final Duration TIMEOUT = Duration.ofSeconds(10);

  private final VapidKeyProvider vapidKeyProvider;
  private final String subject;
  private final Duration timeout;

  private volatile PushService service;
  private volatile String cachedKeysFingerprint;

  @Inject
  public WebPushLibrarySender(
      VapidKeyProvider vapidKeyProvider,
      @ConfigProperty(name = "wyrdly.push.vapid.subject", defaultValue = "mailto:ops@wyrdly.com")
          String subject) {
    this(vapidKeyProvider, subject, TIMEOUT);
  }

  WebPushLibrarySender(VapidKeyProvider vapidKeyProvider, String subject, Duration timeout) {
    this.vapidKeyProvider = vapidKeyProvider;
    this.subject = subject;
    this.timeout = timeout;
  }

  /**
   * Encrypts {@code plaintext} for {@code subscription}, signs the VAPID JWT and POSTs to the push
   * service. Returns the HTTP status code. Blocks up to the configured timeout; callers run on the
   * dispatcher's dedicated pool, never on HTTP worker threads.
   */
  public int send(PushSubscription subscription, byte[] plaintext) throws Exception {
    Notification notification =
        new Notification(
            subscription.endpoint(),
            subscription.p256dh(),
            subscription.auth(),
            new String(plaintext, StandardCharsets.UTF_8));
    Future<HttpResponse> response = service().sendAsync(notification, Encoding.AES128GCM);
    try {
      return response.get(timeout.toMillis(), TimeUnit.MILLISECONDS).getStatusLine().getStatusCode();
    } catch (TimeoutException timedOut) {
      response.cancel(true);
      throw timedOut;
    }
  }

  private PushService service() throws Exception {
    String fingerprint =
        vapidKeyProvider.getPublicKey() + "|" + vapidKeyProvider.getPrivateKey() + "|" + subject;
    PushService snapshot = service;
    if (snapshot != null && fingerprint.equals(cachedKeysFingerprint)) {
      return snapshot;
    }
    synchronized (this) {
      snapshot = service;
      if (snapshot != null && fingerprint.equals(cachedKeysFingerprint)) {
        return snapshot;
      }
      PushService built =
          new PushService(
              vapidKeyProvider.getPublicKey(), rawPrivateScalar(vapidKeyProvider.getPrivateKey()), subject);
      service = built;
      cachedKeysFingerprint = fingerprint;
      return built;
    }
  }

  /**
   * Converts a base64url PKCS#8 EC private key to the raw 32-byte scalar the library expects.
   * Package-private for tests.
   */
  static String rawPrivateScalar(String pkcs8Base64Url) throws Exception {
    byte[] pkcs8 = Base64.getUrlDecoder().decode(pkcs8Base64Url);
    ECPrivateKey key =
        (ECPrivateKey)
            KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
    byte[] scalar = toFixed32(key.getS().toByteArray());
    return Base64.getUrlEncoder().withoutPadding().encodeToString(scalar);
  }

  private static byte[] toFixed32(byte[] src) {
    if (src.length == 32) {
      return src;
    }
    byte[] dst = new byte[32];
    if (src.length > 32) {
      System.arraycopy(src, src.length - 32, dst, 0, 32);
    } else {
      System.arraycopy(src, 0, dst, 32 - src.length, src.length);
    }
    return dst;
  }
}
