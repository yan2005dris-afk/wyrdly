package com.wyrdly.notifications.infrastructure.validation;

import com.wyrdly.notifications.domain.exception.InvalidSubscriptionException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Syntactic guard that rejects unsafe or non-public push endpoints before a
 * {@code PushSubscription} is persisted.
 *
 * <p>Validates the {@code endpoint} URL against the W3C Push API constraints
 * that browser push gateways actually satisfy. Push gateways (FCM, Mozilla
 * Autopush, Apple WebPush) issue HTTPS endpoints under public hostnames; any
 * other shape is either a developer mistake or an attempt to redirect push
 * traffic to an attacker-controlled receiver.
 *
 * <p>This check is intentionally syntactic — it never performs DNS at
 * request time. A hostname that becomes hostile after registration cannot
 * be detected here; that is mitigated by the gateway's 410 Gone response
 * and {@code PushDispatcher}'s auto-cleanup.
 *
 * <p>Rejected shapes:
 *
 * <ul>
 *   <li>scheme other than {@code https}
 *   <li>missing host
 *   <li>userInfo or fragment component present
 *   <li>{@code localhost}, IP literal (v4 or v6), {@code .local}, {@code
 *       .internal}
 *   <li>label longer than 63 chars, leading/trailing hyphen, empty label
 *   <li>less than two labels or TLD that is all-numeric (RFC 3696 §2)
 *   <li>total hostname longer than 253 characters
 *   <li>URI parse failure
 * </ul>
 */
public final class PushEndpointValidator {

  private static final Pattern LABEL_PATTERN =
      Pattern.compile("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?");

  private PushEndpointValidator() {}

  /**
   * Throws {@link InvalidSubscriptionException} when the supplied endpoint
   * does not satisfy the syntactic constraints.
   */
  public static void validate(String endpoint) {
    if (endpoint == null || endpoint.isBlank()) {
      throw new InvalidSubscriptionException("endpoint is required");
    }
    URI uri;
    try {
      uri = new URI(endpoint.trim());
    } catch (URISyntaxException failure) {
      throw new InvalidSubscriptionException(
          "endpoint must be a syntactically valid URI");
    }

    if (!"https".equalsIgnoreCase(uri.getScheme())) {
      throw new InvalidSubscriptionException(
          "endpoint scheme must be https");
    }
    String host = uri.getHost();
    if (host == null || host.isEmpty()) {
      throw new InvalidSubscriptionException("endpoint host is required");
    }
    if (uri.getRawUserInfo() != null) {
      throw new InvalidSubscriptionException(
          "endpoint must not contain userInfo");
    }
    if (uri.getRawFragment() != null) {
      throw new InvalidSubscriptionException(
          "endpoint must not contain a fragment");
    }
    if (!isPublicHostname(host)) {
      throw new InvalidSubscriptionException(
          "endpoint host must be a public, non-loopback hostname");
    }
  }

  /**
   * Public-hostname test: rejects {@code localhost}, IP literals, and the
   * reserved TLDs that resolve only inside a private network.
   *
   * <p>Brackets around an IPv6 literal that may survive {@code URI.getHost()}
   * are stripped before further inspection.
   */
  private static boolean isPublicHostname(String rawHost) {
    String host = rawHost.toLowerCase(Locale.ROOT);
    while (host.endsWith(".")) {
      host = host.substring(0, host.length() - 1);
    }
    if (host.startsWith("[") && host.endsWith("]")) {
      return false;
    }
    if (host.contains(":")) {
      return false;
    }
    if (host.equals("localhost")
        || host.endsWith(".localhost")
        || host.endsWith(".local")
        || host.endsWith(".internal")) {
      return false;
    }
    if (looksLikeIpv4(host)) {
      return false;
    }
    if (host.length() > 253) {
      return false;
    }
    String[] labels = host.split("\\.", -1);
    if (labels.length < 2) {
      return false;
    }
    for (String label : labels) {
      if (label.isEmpty() || label.length() > 63) {
        return false;
      }
      if (!LABEL_PATTERN.matcher(label).matches()) {
        return false;
      }
    }
    String topLevel = labels[labels.length - 1];
    if (topLevel.matches("[0-9]+")) {
      return false;
    }
    return true;
  }

  private static boolean looksLikeIpv4(String host) {
    if (!host.matches("[0-9.]+")) {
      return false;
    }
    String[] octets = host.split("\\.", -1);
    if (octets.length != 4) {
      return false;
    }
    for (String octet : octets) {
      if (octet.isEmpty() || octet.length() > 3) {
        return false;
      }
      try {
        int value = Integer.parseInt(octet);
        if (value < 0 || value > 255) {
          return false;
        }
      } catch (NumberFormatException failure) {
        return false;
      }
    }
    return true;
  }
}