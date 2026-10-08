# Guía de Observabilidad y Telemetría — Wyrdly

Este documento consolida la arquitectura de observabilidad, métricas de Prometheus/Micrometer y dashboards recomendados para el monitoreo en producción de Wyrdly.

---

## 1. Web Push Telemetría (HU11 / OPS-PUSH-4.1)

El despachador de notificaciones Web Push (`PushDispatcherImpl`) instrumenta métricas nativas vía **Micrometer** expuestas en el endpoint Prometheus de Quarkus (`/q/metrics`).

### 1.1 Catálogo de Métricas

| Métrica | Tipo Micrometer | Tags | Unidad | Descripción |
|---|---|---|---|---|
| `wyrdly.push.dispatch` | Counter | `type`, `result` | count | Total acumulado de intentos de despacho Web Push. |
| `wyrdly.push.dispatch.duration` | Timer | `type`, `result` | seconds / ms | Latencia de la solicitud HTTP POST hacia el push gateway. |
| `wyrdly.push.dispatch.bytes` | DistributionSummary | `type` | bytes | Tamaño en bytes del payload cifrado per RFC 8291 (`aes128gcm`). |
| `wyrdly.push.subscriptions` | Gauge | *(global)* | count | Número total de suscripciones push activas registradas en Neo4j. |

### 1.2 Valores del Tag `result`

- `ok`: Notificación entregada exitosamente al gateway del navegador (HTTP 201 Created).
- `http_4xx`: Error de cliente o payload inválido (HTTP 400 Bad Request, etc.).
- `gone`: Suscripción revocada o caducada (HTTP 404 / 410 Gone); el adapter limpia automáticamente los registros en Neo4j.
- `http_5xx`: Falla del gateway remoto del push service (HTTP 500, 502, 503) tras agotar reintentos con backoff exponencial.
- `network_error`: Falla de conectividad a nivel de socket / DNS / timeout.
- `skipped_no_subscription`: Evento generado para un usuario sin suscripción activa registrada.
- `rate_limited`: Despacho throttled para proteger recursos o mitigar tormentas de eventos.
- `failed`: Estado HTTP inesperado (fuera de 2xx/4xx/5xx) o excepción durante el cifrado/despacho (catch genérico en `doDispatch`).

### 1.3 Valores del Tag `type`

- `POST_LIKE`: Reacción a una publicación del usuario.
- `POST_BOOST`: Republicación / boost de un post.
- `GRAPH_FOLLOW`: Notificación de un nuevo seguidor en el grafo.
- `CHAT_MESSAGE`: Mensaje privado cuando el destinatario está offline.
- `NEW_POST_FROM_FOLLOWED`: Nueva publicación de un autor seguido.
- `unknown`: Fallback cuando el evento llega sin `type` (`event.type() == null`). Contrato de cardinalidad: los 5 valores conocidos de arriba más `unknown`; no hay allowlist en código (follow-up pendiente), así que un `type` inesperado desde el frontend crearía una serie nueva — mantener acotado el vocabulario.

---

## 2. Rate Limiting y Protección Anti-Tormenta

Para evitar saturación de la infraestructura o penalizaciones de los servicios de push (FCM, Mozilla Autopush, Apple WebPush):

1. **Cap por Destinatario (`recipientRateIntervalMs`)**:
   - Intervalo mínimo de 1 push por segundo por destinatario (default: `1000 ms`).
   - Evita inundación si un usuario recibe 100 reacciones o comentarios en 1 segundo.
   - Las solicitudes excedentes se descartan inmediatamente y se registra `result="rate_limited"`.

2. **Cap Global (`globalRatePerSecond`)**:
    - Token bucket global configurable mediante `wyrdly.push.dispatch.global-rate-per-second` (default: `1000`).
    - Protege los límites de salida hacia internet y los hilos de I/O.
    - `<= 0` deshabilita el cap global (sin `TokenBucket`, todo pasa al limiter por destinatario).

### Configuración en `application.properties`:
```properties
wyrdly.push.dispatch.global-rate-per-second=1000
wyrdly.push.dispatch.recipient-rate-interval-ms=1000
wyrdly.push.dispatch.max-retries=3
wyrdly.push.dispatch.initial-backoff-ms=200
```

---

## 3. Consultas PromQL Útiles

### Tasa de despacho por resultado (últimos 5 minutos):
```promql
sum by (result) (rate(wyrdly_push_dispatch_total[5m]))
```

### Porcentaje de solicitudes rate-limited:
```promql
sum(rate(wyrdly_push_dispatch_total{result="rate_limited"}[5m]))
/
clamp_min(sum(rate(wyrdly_push_dispatch_total[5m])), 1) * 100
```

### Latencia p95 del Push Service Gateway:
```promql
histogram_quantile(0.95, sum by (le) (rate(wyrdly_push_dispatch_duration_seconds_bucket{result="ok"}[5m])))
```

### Suscripciones activas en Neo4j:
```promql
wyrdly_push_subscriptions
```

---

## 4. Grafana Dashboard Sugerido

Un dashboard para Grafana está disponible en [`docs/observability/hu11-push-dashboard.json`](./hu11-push-dashboard.json) con los siguientes paneles (ids 1:1 con el JSON):
1. **Push Dispatch Rate by Result** (id 1, Timeseries con `ok`, `gone`, `http_4xx`, `http_5xx`, `rate_limited`).
2. **Active Push Subscriptions** (id 2, Stat gauge consultando `wyrdly_push_subscriptions`).
3. **Rate Limited Percentage** (id 3, Stat panel con color de advertencia si la tasa supera umbral).
4. **Push Gateway Latency (p50 / p95 / p99)** (id 4, Timeseries en milisegundos).
5. **Average Encrypted Payload Size** (id 5, Timeseries con promedio `rate(wyrdly_push_dispatch_bytes_sum[5m]) / rate(wyrdly_push_dispatch_bytes_count[5m])` en bytes — promedio porque el `DistributionSummary` plano no publica serie `_bucket`).
