### Estado de Réplica: ACEPTADO CON OBSERVACIONES

Agradecemos la revisión técnica del equipo auditor. Tras verificar la evidencia en el commit auditado y la arquitectura del módulo de reacciones, determinamos lo siguiente:

#### 1. Reconocimiento del problema
Se acepta la degradación de latencia observada durante la caída de la caché:
- La capa de filtros JAX-RS (`ReactionIdempotencyFilter` y `ReactionRateLimitFilter`) realiza llamadas síncronas contra Redis antes de delegar al caso de uso.
- Ante la caída del contenedor `wyrdly-redis`, cada filtro queda sujeto a `quarkus.redis.timeout=2s`. Esto acumula entre 2.0s y 4.0s de bloqueo pasivo en los hilos de Quarkus antes de activar el bloque de captura y continuar.
- Aunque el comportamiento *fail-open* cumple a nivel funcional (la reacción persiste en Neo4j y responde `200 OK`), la ausencia de un mecanismo de corte rápido (*fail-fast*) degrada innecesariamente el tiempo de respuesta.

#### 2. Observaciones técnicas y contextualización
- **Alcance de los Requisitos Mínimos (MVP de Cátedra):**
  El documento base de la actividad práctica no exigía una capa de caché Redis para la persistencia básica de publicaciones y reacciones. Redis fue introducido por nuestro equipo como una optimización arquitectónica proactiva para absorber concurrencia elevada (HU09), implementando deduplicación por idempotencia y rate-limiting por ventana fija.
- **Resiliencia funcional verificada:**
  El diseño garantiza que la verdad transaccional reside exclusivamente en el grafo (Neo4j). Como bien reporta la auditoría, el sistema nunca pierde datos ni bloquea la operación central; simplemente experimenta latencia por espera de socket en la capa de optimización.
- **Reclasificación de Severidad:**
  Proponemos mantener o ajustar la severidad a **Media / Performance Tuning**, reconociendo que la falla se limita a una capa de optimización no troncal que no compromete la consistencia de datos ni la disponibilidad final del endpoint.

#### 3. Acciones correctivas planificadas
1. **Configuración de Circuit Breaker:** Aislar las llamadas de `RedisDataSource` utilizando `@CircuitBreaker` de `quarkus-smallrye-fault-tolerance` (o un breaker en memoria) para abrir el circuito tras fallos consecutivos y evitar la penalización de timeout en peticiones posteriores.
2. **Ajuste de Timeout Fail-Fast:** Reducir `quarkus.redis.timeout` a un umbral de milisegundos (ej. 200ms–300ms) para que el descarte ante fallos de socket sea inmediato y no impacte el p95 del endpoint `/react`.
