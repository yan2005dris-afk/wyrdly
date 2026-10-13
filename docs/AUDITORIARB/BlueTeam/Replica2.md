# Estado de Réplica: ❌ IMPUGNADO

El equipo Blue Team **impugna técnicamente** el hallazgo `SEC-001`. El mecanismo de Rate Limiting existe, está implementado en código, se encuentra activo y corta efectivamente el tráfico con `HTTP 429 Too Many Requests` a partir de la petición 31.

El resultado observado por el Red Team (*todas las peticiones devolvieron 200 OK*) es consecuencia de un error metodológico en la prueba: se realizaron 35 clics rápidos sobre **la misma publicación**, activando la capa de **Idempotencia (Anti Rage-Click)** en lugar de agotar el **Rate Limiter**.

---

## 1. Arquitectura de Protección en Dos Capas (JAX-RS)

En el endpoint `POST /api/posts/{postId}/react`, el backend ejecuta dos filtros ordenados por prioridad:

1. **Capa 1 — `ReactionIdempotencyFilter` (Prioridad `AUTHENTICATION + 100` = 1100):**
    - Diseñado para proteger el clúster ante *rage-clicks* accidentales o maliciosos sobre un mismo post (`ADR-005` §2 y `plan-accion-hu09-reacciones.md` §5.1).
    - Genera una clave Redis `react:{userId}:{postId}:{type}` con TTL de 1 segundo.
    - Si detecta clics repetidos en ese intervalo, interrumpe la cadena con `req.abortWith(...)` y devuelve la respuesta cacheada (`HTTP 200 OK`) con la cabecera:
        ```http
        X-Cached-Response: true
        ```

2. **Capa 2 — `ReactionRateLimitFilter` (Prioridad `AUTHENTICATION + 200` = 1200):**
    - Implementa Token Bucket en Redis (`ratelimit:react:{userId}`) con cuota estricta de **30 reacciones por ventana de 60 segundos**.
    - Por especificación formal de diseño (`ReactionRateLimitFilter.java`, líneas 20–22):
        > *"Runs after ReactionIdempotencyFilter so idempotency replays don't consume budget."*
    - Al abortar la Capa 1 mediante `req.abortWith()`, las peticiones duplicadas no ejecutan la Capa 2 y **no consumen la cuota del usuario**.

---

## 2. Demostración Técnica: Qué Ocurrió en su Prueba

En el paso 3 del reporte indican:
> *"Hacer clic repetidamente y a gran velocidad (más de 35 veces) sobre el botón de reacción de un post."*

Al ejecutar esa acción en un lapso de pocos segundos:
- **Clic 1:** Ejecuta la transacción en base de datos, devuelve `200 OK` y guarda el resultado en caché Redis. El contador de Rate Limit sube a **1**.
- **Clics 2 al 35:** Ocurren dentro del segundo de TTL. Son absorbidos por el `ReactionIdempotencyFilter`, devolviendo `200 OK` con cabecera `X-Cached-Response: true`. 
- **Resultado en Redis:** El contador de Rate Limit permaneció en **1** (no en 35). Ninguno de los 34 clics duplicados golpeó a Neo4j ni agotó la cuota. El sistema operó con el comportamiento exacto para el que fue diseñado.

---

## 3. Evidencia Empírica de Funcionamiento del Rate Limiter (HTTP 429)

Se verificó el comportamiento del endpoint mediante una suite de pruebas automatizadas contra una instancia real de Redis:

1. **Prueba A (Peticiones efectivas a publicaciones distintas):**
    - Peticiones 1 a 30: `HTTP 200 OK`
    - Petición 31: **`HTTP 429 Too Many Requests`**
    - Cabecera recibida: `Retry-After: 60`
    - Payload recibido: `{"code":"RATE_LIMITED"}`

2. **Prueba B (35 clics rápidos a la misma publicación):**
    - Petición 1: `HTTP 200 OK` (Rate limit counter = 1)
    - Peticiones 2 a 35: `HTTP 200 OK` (`X-Cached-Response: true`)
    - Rate limit counter final en Redis: **1** (cero consumo indebido de cuota).

---

## 4. Reproducción Independiente

Para comprobar que el Rate Limiter bloquea efectivamente con `429`, ejecutar este script contra el backend con Redis activo enviando peticiones a recursos distintos (sin colisión de idempotencia):

```bash
TOKEN="<JWT_TOKEN_VALIDO>"

for i in $(seq 1 35); do
    CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "http://localhost:8080/api/posts/pst_${i}/react" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"type":"LIKE"}')
    echo "Petición $i -> HTTP $CODE"
done
```

Salida esperada:

• Petición 1 a 30 -> HTTP 200
• Petición 31 a 35 -> HTTP 429

---

## 5. Conclusión

El endpoint no carece de Rate Limiting ni permite el abuso del recurso; cuenta con una defensa en profundidad donde la idempotencia mitiga el rage-click y el rate limiter mitiga la saturación transaccional. Solicitamos la retirada de este hallazgo.
