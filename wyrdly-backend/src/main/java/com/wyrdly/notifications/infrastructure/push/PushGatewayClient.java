package com.wyrdly.notifications.infrastructure.push;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Thin wrapper around the JDK {@link HttpClient} for posting an encrypted Web Push payload to a
 * Push Service URL. Exists as a separate class so the dispatcher can be tested with a fake (or so
 * an integration test can swap in a {@code com.github.tomakehurst.wiremock} client if we add one
 * later).
 */
@ApplicationScoped
public class PushGatewayClient {

  private final HttpClient client;
  private final Duration requestTimeout;

  public PushGatewayClient() {
    this(Duration.ofSeconds(10));
  }

  public PushGatewayClient(Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    this.client =
        HttpClient.newBuilder()
            .connectTimeout(requestTimeout)
            .version(HttpClient.Version.HTTP_1_1)
            .build();
  }

  /** Returns the HTTP status code returned by the Push Service. */
  public int post(URI url, byte[] body, Map<String, String> headers) throws IOException {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(url)
            .timeout(requestTimeout)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body));
    headers.forEach(builder::header);
    try {
      HttpResponse<String> response =
          client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      return response.statusCode();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IOException("Push gateway request interrupted", ex);
    }
  }
}
