### Estado de Réplica: ACEPTADO CON OBSERVACIONES

Agradecemos la revisión técnica del equipo auditor. Tras analizar el comportamiento de la capa de API y la gestión de estado en el cliente ante la caída simulada del grafo principal, determinamos lo siguiente:

#### 1. Reconocimiento del problema
Se acepta la anomalía en el manejo de excepciones y la degradación de la experiencia de usuario:
- El backend carece de un `ServerExceptionMapper` específico para capturar `ServiceUnavailableException` o `SessionExpiredException` del driver de Neo4j en la capa de autenticación (`AuthExceptionMappers.java`), provocando que Quarkus responda con un error no tipificado `500 Internal Server Error`.
- En el frontend, el interceptor de refresco de sesión (`axios.ts`) y el `AuthContext.tsx` capturan el fallo de conexión HTTP como una expiración de credenciales, emitiendo el evento `wyrdly:session-expired` y purgando el estado local del usuario.
- En la vista de login (`AuthFormCard.tsx`), al no haber una tipificación semántica de base de datos indisponible, los intentos de reingreso colisionan con estados residuales en la UI generando mensajes contradictorios.

#### 2. Observaciones técnicas y contextualización
- **Contexto de Desarrollo e Integración (MVP en 1 semana):**
  El objetivo principal del sprint estuvo centrado en garantizar la completitud funcional de los flujos core (registro, autenticación JWT con firma de claves asimétricas, grafo social de seguidores y timeline distribuido). Los flujos de fallo de infraestructura se delegaron temporalmente a los manejadores de error globales de Quarkus.
- **Naturaleza del Fallo (Infraestructura vs. Lógica de Negocio):**
  La detención física del contenedor `wyrdly-neo4j` constituye una falla total de infraestructura crítica (Single Point of Failure de la base de datos). El sistema no corrompe datos ni sufre brechas de seguridad; la deficiencia radica en la propagación semántica del error hacia el cliente y la preservación del estado de sesión en modo degradado.
- **Reclasificación de Severidad:**
  Si bien la caída de la base de datos es un escenario crítico, la severidad del defecto en código corresponde a **Media / Error Handling & Resilience**, ya que la falla raíz es la ausencia de un código HTTP semántico (`503 Service Unavailable`) y una pantalla de degradación elegante en el frontend.

#### 3. Acciones correctivas planificadas
1. **Exception Mapper Global para Neo4j (Backend):** Implementar un `@ServerExceptionMapper` para capturar `ServiceUnavailableException` y retornar un código `HTTP 503 Service Unavailable` con cuerpo estructurado (`{"code": "DATABASE_UNAVAILABLE", "message": "Database is temporarily unreachable"}`).
2. **Discriminación de Errores de Infraestructura en Cliente (Frontend):** Actualizar `apiErrors.ts` y el interceptor de Axios en `axios.ts` para que errores 503/502 no purguen el `localStorage` ni disparen `session-expired`.
3. **Banner / Fallback de Mantenimiento:** Mostrar en `AuthFormCard` y en el layout principal un banner explícito de "Servicio en mantenimiento / Base de datos no disponible" cuando se reciba código 503, bloqueando reintentos erróneos sin confundir al usuario con fallos de credenciales.
