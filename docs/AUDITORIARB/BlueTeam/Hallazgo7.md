**ID:** RES-006
**Categoría:** Resiliencia (Chaos Mode)
**Severidad:** Media

### Descripción
El documento de diseño indica que ante una caída de Redis, el sistema de reacciones hace *Fail open* (permite la operación sin validación de caché). Aunque esto técnicamente se cumple (la ruta `/react` devuelve 200 OK), la implementación carece de un *Circuit Breaker* o configuración de timeout adecuada, provocando una degradación masiva del rendimiento.

### Pasos exactos para reproducir
1. Iniciar el entorno completo y detener únicamente la caché: `docker stop wyrdly-redis`.
2. Desde el frontend, intentar interactuar con el botón de "Me gusta" de una publicación.
3. Observar los tiempos de respuesta (Time) en la pestaña Network de las DevTools.

### Resultado esperado
El sistema detecta rápidamente la caída de Redis (preferiblemente aislando el fallo con un Circuit Breaker) y procesa la reacción en la base de datos con un impacto mínimo en los tiempos de respuesta.

### Resultado obtenido
Cada petición a `/react` experimenta demoras críticas (entre 1.5 y 5.0 segundos por solicitud) debido a que el hilo del backend espera pasivamente el *Connection Timeout* de Redis antes de continuar con la ejecución. En un entorno productivo, este bloqueo de hilos saturaría rápidamente el servidor Quarkus provocando una caída en cascada.
