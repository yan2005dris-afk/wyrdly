# ⚔️ Hallazgos Técnicos de Auditoría y Pentesting — Red Team
**Proyecto:** Red Social Distribuida (Quarkus 3, Neo4j, Redis, MinIO, Nginx, React 19)  
**Alcance:** Misiones M1, M2, M3, M4, M6  
**Documento complementario a:** `docs/AUDITORIA.docx` (Hallazgos H-28 a H-34)

---

## Índice de Hallazgos Detallados

1. [H-28 (M4) — Fuga de Coordenadas GPS y Metadatos EXIF en Carga de Imágenes](#h-28-m4--fuga-de-coordenadas-gps-y-metadatos-exif-en-carga-de-imágenes)
2. [H-29 (M4) — Exposición de JWT en URL de WebSockets y Falta de Revocación de Sesiones en Servidor](#h-29-m4--exposición-de-jwt-en-url-de-websockets-y-falta-de-revocación-de-sesiones-en-servidor)
3. [H-30 (M4) — Ausencia de Validación de Correo Electrónico y Complejidad de Contraseña en Registro](#h-30-m4--ausencia-de-validación-de-correo-electrónico-y-complejidad-de-contraseña-en-registro)
4. [H-31 (M6) — Generación de Objetos Huérfanos en MinIO ante Fallos de Proceso Backend](#h-31-m6--generación-de-objetos-huérfanos-en-minio-ante-fallos-de-proceso-backend)
5. [H-32 (M2) — Inmutabilidad Forzada: Imposibilidad de Editar o Eliminar Publicaciones y Comentarios](#h-32-m2--inmutabilidad-forzada-imposibilidad-de-editar-o-eliminar-publicaciones-y-comentarios)
6. [H-33 (M1) — Sembrado de Datos Acoplado a Node.js en Host y Respaldos Limitados a PowerShell](#h-33-m1--sembrado-de-datos-acoplado-a-nodejs-en-host-y-respaldos-limitados-a-powershell)
7. [H-34 (M3) — Contención de Hilos por Bloqueo Synchronized Global en ChatSessions](#h-34-m3--contención-de-hilos-por-bloqueo-synchronized-global-en-chatsessions)

---

## H-28 (M4) — Fuga de Coordenadas GPS y Metadatos EXIF en Carga de Imágenes

### 1. Contexto Arquitectónico y Código Fuente
En el backend, el flujo de subida de publicaciones con imagen atraviesa dos componentes:
* [`ImageValidator.java`](file:///home/andy/Escritorio/PROYECTOS/ZENIT/red-social-distribuida/backend/src/main/java/com/redsocial/media/ImageValidator.java): Valida que el archivo no esté vacío, que pese menos de 5 MiB, que las dimensiones no superen 20 megapíxeles y que el formato sea PNG, JPEG o GIF leyendo el primer fotograma con `ImageReader.read(0)`.
* [`PostService.java`](file:///home/andy/Escritorio/PROYECTOS/ZENIT/red-social-distribuida/backend/src/main/java/com/redsocial/posts/PostService.java#L52-L54): Recibe el archivo temporal original (`Path file`) y lo transfiere directamente a MinIO:
  ```java
  var image = images.validate(file, declaredType);
  type = image.contentType();
  key = "posts/" + id + "/" + UUID.randomUUID() + "." + image.extension();
  storage.upload(key, file, type); // <-- El archivo original se sube sin alteraciones
  ```
* [`Nginx (nginx.conf)`](file:///home/andy/Escritorio/PROYECTOS/ZENIT/red-social-distribuida/frontend/nginx.conf#L103-L107): Despacha las imágenes directamente desde MinIO a cualquier visitante mediante `GET /media/*`.

### 2. Mecanismo de Vulnerabilidad
El formato JPEG (y TIFF/HEIC) almacena metadatos en bloques de cabecera estandarizados bajo el formato **EXIF** (Exchangeable Image File Format). Cuando un usuario toma una fotografía con un teléfono inteligente o cámara digital moderna con geolocalización activa, el dispositivo incrusta:
* **Coordenadas GPS exactas**: Latitud, Longitud, Altitud y Timestamp UTC de la toma.
* **Información del dispositivo**: Marca, modelo, versión de firmware, número de serie de lente.
* **Información ambiental**: Condiciones de luz, flash, orientación.

Debido a que `PostService.java` toma el flujo de bytes binarios original del cliente y lo deposita directamente en el bucket de S3 sin procesar una re-renderización limpia de los píxeles, **todos los metadatos EXIF se conservan íntegros**.

### 3. Escenario de Explotación (PoC)
1. Un usuario toma una foto en su domicilio desde su teléfono y la publica en la red social.
2. Un atacante inspecciona la publicación en el feed y obtiene la URL pública de la imagen:
   ```bash
   curl -s -O http://localhost:8080/media/posts/4f8b2d12-8e3b-4c28-98e2-1a4b5c6d7e8f/c9a1b2c3.jpg
   ```
3. El atacante ejecuta una herramienta de extracción forense de metadatos:
   ```bash
   exiftool c9a1b2c3.jpg | grep -i GPS
   ```
4. **Salida obtenida por el atacante:**
   ```text
   GPS Latitude                    : -0 deg 11' 24.12" S
   GPS Longitude                   : -78 deg 29' 48.84" W
   GPS Position                    : -0.190033, -78.496900
   Make                            : Apple
   Camera Model Name               : iPhone 15 Pro
   Date/Time Original              : 2026:10:11 18:32:14
   ```
5. El atacante copia las coordenadas en Google Maps / OpenStreetMap y obtiene la dirección física exacta del usuario.

### 4. Clasificación e Impacto
* **CWE-200**: Exposure of Sensitive Information to an Unauthorized Actor.
* **Impacto en Privacidad**: Doxxing, acoso físico (stalking) y perfilado no consentido de usuarios en una red social.

### 5. Recomendación y Parche de Código
En lugar de pasar el archivo original a `storage.upload()`, se debe reescribir la imagen descartando las cabeceras EXIF, o limpiarla usando `ImageIO` o una biblioteca dedicada como Apache Commons Imaging:
```java
// Ejemplo de sanitización en ImageValidator o PostService:
BufferedImage sanitizedImage = reader.read(0);
Path sanitizedFile = Files.createTempFile("sanitized-", "." + type.extension());
ImageIO.write(sanitizedImage, type.extension(), sanitizedFile.toFile());
storage.upload(key, sanitizedFile, type.contentType());
Files.deleteIfExists(sanitizedFile);
```

---

## H-29 (M4) — Exposición de JWT en URL de WebSockets y Falta de Revocación de Sesiones en Servidor

### 1. Contexto Arquitectónico y Código Fuente
* [`frontend/src/services/chat.ts:52-57`](file:///home/andy/Escritorio/PROYECTOS/ZENIT/red-social-distribuida/frontend/src/services/chat.ts#L52-L57):
  ```typescript
  socketUrl: (token: string) => {
    const url = new URL("/ws/chat", window.location.href);
    url.protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    url.searchParams.set("token", token);
    return url.toString();
  }
  ```
* [`backend/src/main/resources/application.properties:23`](file:///home/andy/Escritorio/PROYECTOS/ZENIT/red-social-distribuida/backend/src/main/resources/application.properties#L23):
  ```properties
  smallrye.jwt.new-token.lifespan=${JWT_LIFESPAN_SECONDS:86400} # 24 horas
  ```
* [`backend/src/main/java/com/redsocial/auth/AuthResource.java`](file:///home/andy/Escritorio/PROYECTOS/ZENIT/red-social-distribuida/backend/src/main/java/com/redsocial/auth/AuthResource.java): Solo expone `/registro` y `/login`.

### 2. Mecanismo de Vulnerabilidad
Existen dos fallas convergentes de severidad alta:
1. **Transmisión de credenciales en la URL (CWE-598)**:
   La API estándar `new WebSocket(url)` del navegador no permite agregar cabeceras personalizadas (`Authorization: Bearer <token>`). El frontend sortea esta limitación adjuntando el JWT en la query string (`?token=...`). Aunque Nginx en el contenedor tiene configurado `access_log without_query`, cualquier elemento intermedio de red (proxies corporativos de salida, firewalls de inspección profunda, CDNs externas o el túnel de Cloudflare) registra URLs completas en sus logs de acceso.
2. **Ausencia total de revocación de tokens (CWE-613)**:
   El backend es 100% stateless para autenticación. Cuando el usuario pulsa "Cerrar sesión", el cliente únicamente ejecuta `localStorage.removeItem("nodouni.session")`. El servidor **no se entera** del cierre de sesión. Si el token fue capturado de los logs de red o extraído de `localStorage`, permanece válido para ejecutar cualquier operación REST (`POST /posts`, `GET /usuarios/me`, etc.) o conectarse al WebSocket durante 24 horas completas.

### 3. Escenario de Explotación (PoC)
1. Un usuario inicia sesión en una computadora compartida de la universidad o biblioteca y usa el chat.
2. El usuario hace clic en "Cerrar sesión" y se retira.
3. Un atacante revisa el historial de red de las herramientas de desarrollador o el log del proxy local y copia la URL del WebSocket:
   `ws://localhost:8080/ws/chat?token=eyJhbGciOiJSUzI1NiIs...`
4. El atacante extrae el token y ejecuta peticiones autenticadas contra la API REST:
   ```bash
   TOKEN="eyJhbGciOiJSUzI1NiIs..."
   curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/usuarios/me
   ```
5. La petición responde `200 OK` con todos los datos privados del usuario, horas después de que este cerrara sesión.

### 4. Clasificación e Impacto
* **CWE-598**: Use of GET Request Method With Sensitive Query Strings.
* **CWE-613**: Insufficient Session Expiration.
* **Impacto**: Secuestro de sesión persistente (Session Hijacking) y suplantación de identidad sin posibilidad de remediación por parte de la víctima.

### 5. Recomendación y Parche de Código
1. **Para WebSockets**: En lugar de enviar el JWT persistente de 24 horas en la URL, implementar un endpoint de ticket de un solo uso:
   * Cliente llama `POST /api/chat/ticket` con cabecera `Authorization: Bearer <JWT>`.
   * Backend genera un UUID efímero en Redis con TTL de 30 segundos asociado al `userId`.
   * Cliente conecta a `/ws/chat?ticket=<UUID>`. El servidor consume y borra el ticket inmediatamente.
2. **Para Revocación de Sesión**:
   * Implementar `POST /api/auth/logout`.
   * Extraer el identificador del token (`jti`) o el par `userId + iat` y guardarlo en Redis con expiración igual al tiempo restante de vida del token.
   * En [`SecurityEventFilter.java`](file:///home/andy/Escritorio/PROYECTOS/ZENIT/red-social-distribuida/backend/src/main/java/com/redsocial/auth/SecurityEventFilter.java), comprobar si el token está en la lista negra antes de admitir la petición.

---

## H-30 (M4) — Ausencia de Validación de Correo Electrónico y Complejidad de Contraseña en Registro

### 1. Contexto Arquitectónico y Código Fuente
* [`backend/src/main/java/com/redsocial/auth/RegisterRequest.java`](file:///home/andy/Escritorio/PROYECTOS/ZENIT/red-social-distribuida/backend/src/main/java/com/redsocial/auth/RegisterRequest.java#L8-L13):
  ```java
  public record RegisterRequest(
          @NotBlank(message = "es obligatorio") String username,
          @NotBlank(message = "es obligatorio") String email,
          @NotBlank(message = "es obligatorio") String password,
          @NotBlank(message = "es obligatorio") String nombre) {
  }
  ```
* [`backend/src/main/java/com/redsocial/auth/AuthService.java:32-49`](file:///home/andy/Escritorio/PROYECTOS/ZENIT/red-social-distribuida/backend/src/main/java/com/redsocial/auth/AuthService.java#L32-L49): Toma `request.password()`, ejecuta `BcryptUtil.bcryptHash(...)` y lo guarda directamente en Neo4j sin validar longitud ni entropía.

### 2. Mecanismo de Vulnerabilidad
La única validación declarativa aplicada es `@NotBlank`, la cual se limita a comprobar que el String no sea nulo y contenga al menos un carácter que no sea espacio en blanco. No se aplica:
* Validación de sintaxis de correo electrónico (RFC 5322).
* Reglas de longitud mínima (por estándar NIST SP 800-63B se exige al menos 8 caracteres).
* Detección de contraseñas triviales.

### 3. Escenario de Explotación (PoC)
Ejecutar la siguiente llamada REST:
```bash
curl -i -X POST http://localhost:8080/api/auth/registro \
  -H "Content-Type: application/json" \
  -d '{
    "username": "cuentafake",
    "email": "esto_no_es_un_correo",
    "password": "x",
    "nombre": "Usuario Inseguro"
  }'
```
**Respuesta del servidor:**
```http
HTTP/1.1 201 Created
Content-Type: application/json

{
  "id": "e3b8a1c4-...",
  "username": "cuentafake",
  "email": "esto_no_es_un_correo",
  "nombre": "Usuario Inseguro",
  "bio": null
}
```
El usuario se crea exitosamente con contraseña `"x"` y correo malformado.

### 4. Clasificación e Impacto
* **CWE-521**: Weak Password Requirements.
* **CWE-20**: Improper Input Validation.
* **Impacto**: Cuentas vulnerables a ataques automatizados de fuerza bruta con un solo intento, registros de spam y bases de datos degradadas con datos sintácticamente inválidos.

### 5. Recomendación y Parche de Código
Actualizar `RegisterRequest.java` con las anotaciones de Hibernate Validator:
```java
public record RegisterRequest(
        @NotBlank(message = "el username es obligatorio")
        @Size(min = 3, max = 30, message = "el username debe tener entre 3 y 30 caracteres")
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "el username solo puede contener letras, números y guión bajo")
        String username,

        @NotBlank(message = "el email es obligatorio")
        @Email(message = "el email debe ser una dirección válida")
        String email,

        @NotBlank(message = "la contraseña es obligatoria")
        @Size(min = 8, max = 128, message = "la contraseña debe tener entre 8 y 128 caracteres")
        String password,

        @NotBlank(message = "el nombre es obligatorio")
        String nombre) {
}
```

---

## H-31 (M6) — Generación de Objetos Huérfanos en MinIO ante Fallos de Proceso Backend

### 1. Contexto Arquitectónico y Código Fuente
En [`PostService.java:52-68`](file:///home/andy/Escritorio/PROYECTOS/ZENIT/red-social-distribuida/backend/src/main/java/com/redsocial/posts/PostService.java#L52-L68):
```java
if (file != null) {
    var image = images.validate(file, declaredType);
    type = image.contentType();
    key = "posts/" + id + "/" + UUID.randomUUID() + "." + image.extension();
    try {
        storage.upload(key, file, type); // PASO 1: Subida exitosa a MinIO
    } catch (RuntimeException failure) {
        cleanup(key);
        throw new ApiException(503, "ALMACENAMIENTO_NO_DISPONIBLE", ...);
    }
}
PostRepository.StoredPost stored;
try {
    // PASO 2: Escritura en Neo4j
    stored = repository.create(id, authorId, normalized, key, type).orElseThrow(...);
} catch (RuntimeException failure) {
    if (key != null) {
        cleanup(key); // Limpieza reactiva en caso de excepción de Java
    }
    throw failure;
}
```

### 2. Mecanismo de Vulnerabilidad
El patrón implementado es una compensación en memoria basada en bloques `try/catch`. En un sistema distribuido con múltiples contenedores, este mecanismo falla ante los siguientes eventos reales:
1. **Caída del proceso o reinicio forzado del contenedor**: Si Docker ejecuta un OOM Killer sobre `backend-1`, o el proceso sufre un fallo del sistema operativo, el hilo es abortado inmediatamente (`SIGKILL`). El bloque `catch` **nunca se ejecuta**.
2. **Caída de red hacia Neo4j**: Si la conexión con Neo4j se congela por timeout de socket durante el commit, la operación queda en un estado indeterminado.
3. **Ausencia de Garbage Collector en S3**: No existe ningún proceso en segundo plano que compare los archivos físicos presentes en el bucket `media` contra los nodos `Post` existentes en Neo4j.

### 3. Escenario de Explotación / Falla
1. Un usuario envía una petición `POST /api/posts` con una imagen pesada de 4.8 MiB.
2. MinIO almacena la imagen correctamente en `posts/<id>/<uuid>.png`.
3. Inmediatamente después del `upload`, el contenedor sufre un reinicio o fallo de contención de memoria.
4. Neo4j jamás recibe el nodo `Post`.
5. MinIO conserva el archivo de 4.8 MiB en disco indefinidamente.
6. A lo largo de meses de operación, se acumulan gigabytes de imágenes no referenciadas que continúan consumiendo cuota y almacenamiento sin ser visibles en la aplicación.

### 4. Clasificación e Impacto
* **Falla de Consistencia Eventual y Resiliencia en Sistemas Distribuidos**.
* **Fuga de Recursos (Resource Leak)**: Consumo descontrolado de almacenamiento S3 sin trazabilidad en la capa de datos.

### 5. Recomendación y Parche de Código
Implementar un scheduler de reconciliación en Quarkus:
```java
@ApplicationScoped
public class MediaReconciliationJob {
    @Inject MediaStorage storage;
    @Inject Driver driver;

    @Scheduled(cron = "0 0 3 * * ?") // Ejecutar todas las noches a las 3:00 AM
    void purgeOrphanMedia() {
        List<String> s3Keys = storage.listKeys("posts/");
        for (String key : s3Keys) {
            boolean exists = driver.executableQuery("RETURN EXISTS { (:Post {mediaKey: $k}) } AS found")
                    .withParameters(Map.of("k", key))
                    .execute().records().getFirst().get("found").asBoolean();
            if (!exists && isOlderThan24Hours(key)) {
                storage.delete(key);
                LOG.infof("Objeto huérfano eliminado de MinIO: %s", key);
            }
        }
    }
}
```

---

## H-32 (M2) — Inmutabilidad Forzada: Imposibilidad de Editar o Eliminar Publicaciones y Comentarios

### 1. Contexto Arquitectónico y Código Fuente
* [`PostResource.java`](file:///home/andy/Escritorio/PROYECTOS/ZENIT/red-social-distribuida/backend/src/main/java/com/redsocial/posts/PostResource.java): Solo declara `@POST create`, `@GET find`, `@POST react`, `@DELETE unreact`.
* [`CommentResource.java`](file:///home/andy/Escritorio/PROYECTOS/ZENIT/red-social-distribuida/backend/src/main/java/com/redsocial/comentarios/CommentResource.java): Solo declara `@POST create`, `@GET thread`.

### 2. Mecanismo de Vulnerabilidad
El sistema impone una inmutabilidad total sobre las publicaciones y los comentarios. No existe lógica de negocio, métodos en repositorios, endpoints en la API ni controles en la interfaz de usuario para:
* Eliminar una publicación propia (`DELETE /api/posts/{id}`).
* Editar el texto de una publicación propia (`PUT /api/posts/{id}`).
* Eliminar un comentario propio (`DELETE /api/posts/{id}/comentarios/{commentId}`).

### 3. Escenario de Impacto
1. Un usuario publica accidentalmente un mensaje con información sensible (un número de teléfono, credenciales o una fotografía privada).
2. El usuario busca la opción para borrar la publicación y no existe en la interfaz.
3. El usuario intenta forzar la eliminación por consola:
   ```bash
   curl -X DELETE http://localhost:8080/api/posts/f18e2a3b-... -H "Authorization: Bearer <TOKEN>"
   ```
4. El servidor responde: `HTTP 405 Method Not Allowed`.
5. La información queda expuesta permanentemente en el feed de todos sus seguidores y en la consulta `descubrir`.

### 4. Clasificación e Impacto
* **Incumplimiento de Requerimientos Funcionales Esenciales**.
* **Incumplimiento Regulatorio (GDPR / Derechos ARCO)**: Violación del principio del "Derecho al Olvido" (Derecho de Supresión), que exige que los usuarios puedan eliminar los datos y contenidos que hayan generado en la plataforma.

### 5. Recomendación y Parche de Código
Agregar el endpoint de eliminación verificando la autoría:
```java
@DELETE
@Path("/{id}")
@Authenticated
public Response deletePost(@PathParam("id") String id) {
    service.deletePost(jwt.getSubject(), id);
    return Response.noContent().build();
}
```
En `PostService`:
```java
public void deletePost(String actorId, String postId) {
    var post = repository.find(postId, actorId).orElseThrow(PostService::postNotFound);
    if (!post.autor().id().equals(actorId)) {
        throw ApiException.forbidden("NO_AUTORIZADO", "No puedes eliminar publicaciones de otros usuarios");
    }
    repository.delete(postId);
    if (post.mediaKey() != null) {
        storage.delete(post.mediaKey());
    }
}
```
En `PostRepository` con Cypher:
```cypher
MATCH (p:Post {id: $postId})
OPTIONAL MATCH (p)<-[:EN]-(c:Comentario)
DETACH DELETE c, p
```

---

## H-33 (M1) — Sembrado de Datos Acoplado a Node.js en Host y Respaldos Limitados a PowerShell

### 1. Contexto Arquitectónico y Código Fuente
* [`scripts/seed-demo.mjs`](file:///home/andy/Escritorio/PROYECTOS/ZENIT/red-social-distribuida/scripts/seed-demo.mjs): Script en Node.js que realiza llamadas REST para poblar usuarios y validar C1-C8.
* [`README.md:107, 150-184`](file:///home/andy/Escritorio/PROYECTOS/ZENIT/red-social-distribuida/README.md#L107):
  * Línea 107: *"Node.js 18 o superior, solo para cargar los datos de demostración."*
  * Líneas 155-165: Scripts de respaldo escritos exclusivamente en sintaxis Microsoft PowerShell (`$backupDir = Join-Path ...`, `New-Item ...`, `Get-ChildItem ...`).

### 2. Mecanismo de Falla
1. **Falta de encapsulamiento en Docker**: Una de las premisas centrales de Docker Compose es la portabilidad sin dependencias locales en el host (*zero-dependency execution*). Exigir Node.js en el host rompe la encapsulación para evaluadores que corren en servidores limpios o contenedores de prueba.
2. **Discriminación de sistema operativo en operaciones críticas**: Los comandos de backup y restore están diseñados exclusivamente para entornos Windows. Un evaluador o administrador en Linux/macOS no puede ejecutar dichos comandos directamente sin tener que traducir manualmente las variables y cmdlets de PowerShell a comandos bash (`tar`, `mkdir -p`, etc.).

### 3. Escenario de Reproducción
1. Descargar el repositorio en un servidor Linux que solo tiene Docker y Compose instalados (sin Node.js ni PowerShell).
2. Ejecutar `docker compose up -d --build`.
3. Intentar ejecutar el sembrado: `node scripts/seed-demo.mjs` → `bash: node: command not found`.
4. Intentar ejecutar el procedimiento de respaldo del README: `bash: syntax error near unexpected token '$sourceProject'`.

### 4. Clasificación e Impacto
* **Falla de Portabilidad y Reproducibilidad en Entornos Heterogéneos**.
* **Fricción Operativa para Administradores de Sistemas Linux**.

### 5. Recomendación y Parche de Código
1. **Contenedorizar el seed en `docker-compose.yml`**:
   ```yaml
   seed:
     image: node:22-alpine
     profiles: ["demo", "seed"]
     depends_on:
       frontend:
         condition: service_healthy
     volumes:
       - ./scripts:/scripts:ro
     command: ["node", "/scripts/seed-demo.mjs", "http://frontend:80"]
     networks:
       - social
   ```
   *Ahora se ejecuta en cualquier equipo con:* `docker compose --profile seed run --rm seed`.
2. **Incluir script de backup multiplataforma en Bash (`scripts/backup.sh`)**:
   ```bash
   #!/usr/bin/env bash
   set -euo pipefail
   BACKUP_DIR="${HOME}/red-social-respaldo-$(date +%Y%m%d-%H%M%S)"
   mkdir -p "$BACKUP_DIR"
   docker compose down
   for VOL in neo4j_data minio_data app_keys; do
       PROJECT_VOL="$(docker compose config --format json | jq -r .name)_${VOL}"
       docker run --rm -v "${PROJECT_VOL}:/source:ro" -v "${BACKUP_DIR}:/backup" alpine:3.20 tar -czf "/backup/${VOL}.tar.gz" -C /source .
   done
   docker compose up -d
   echo "Respaldo completado en $BACKUP_DIR"
   ```

---

## H-34 (M3) — Contención de Hilos por Bloqueo Synchronized Global en ChatSessions

### 1. Contexto Arquitectónico y Código Fuente
En [`backend/src/main/java/com/redsocial/chat/ChatSessions.java:21-39`](file:///home/andy/Escritorio/PROYECTOS/ZENIT/red-social-distribuida/backend/src/main/java/com/redsocial/chat/ChatSessions.java#L21-L39):
```java
@ApplicationScoped
public class ChatSessions {
    private static final Logger LOG = Logger.getLogger(ChatSessions.class);
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, Boolean> delivered = new LinkedHashMap<>(); // <-- Mapa de deduplicación

    public void deliver(ChatEnvelope envelope) {
        synchronized (delivered) { // <-- Bloqueo intrínseco global
            if (delivered.putIfAbsent(envelope.mensaje().id(), true) != null) {
                return;
            }
            if (delivered.size() > 4096) {
                delivered.remove(delivered.keySet().iterator().next());
            }
        }
        var event = new ChatEvent(envelope.mensaje());
        for (var session : sessions.values()) {
            ...
```

### 2. Mecanismo de Vulnerabilidad
El método `deliver` es el punto de entrada para cada mensaje que llega desde Redis Pub/Sub a través de la suscripción reactiva. Para evitar que un mensaje se entregue por duplicado, se utiliza un `LinkedHashMap` como caché LRU acotada a 4,096 elementos.

Sin embargo, para garantizar la seguridad de hilos sobre `LinkedHashMap` (que no es concurrente por diseño), se implementó un candado monolítico: `synchronized (delivered)`.
* En un escenario de alta concurrencia con cientos de usuarios chateando simultáneamente en distintas conversaciones, **todos los hilos de recepción del broker se detienen y hacen fila para adquirir el mismo cerrojo de monitor**.
* Si la tasa de mensajes entrantes es alta, este candado genera contención de hilos (*thread contention*), incrementando la latencia de entrega de mensajes a través de los WebSockets y degradando el event loop de Vert.x/Quarkus.

### 3. Escenario de Falla / Rendimiento
Bajo una prueba de carga en JMeter o Locust donde 1,000 usuarios virtuales intercambian mensajes simultáneamente:
1. Las llamadas a `deliver()` saturan el monitor `delivered`.
2. Los hilos de I/O reactivos de Quarkus se bloquean esperando el candado.
3. Se producen picos de latencia (*latency spikes*) en la entrega en vivo de mensajes de chat.

### 4. Clasificación e Impacto
* **Anti-patrón de Concurrencia**: *Lock Contention on Shared In-Memory Data Structure*.
* **Impacto en Escalabilidad**: Degrada el rendimiento del backend bajo alta carga concurrente, limitando los beneficios del modelo reactivo de Vert.x.

### 5. Recomendación y Parche de Código
Reemplazar el `LinkedHashMap` sincronizado manualmente por una caché concurrente moderna sin contención de bloqueo global, como **Caffeine** (disponible de forma nativa en Quarkus):
```java
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@ApplicationScoped
public class ChatSessions {
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    
    // Caché concurrente con desalojo LRU automático y alta concurrencia lock-free:
    private final Cache<String, Boolean> delivered = Caffeine.newBuilder()
            .maximumSize(4096)
            .build();

    public void deliver(ChatEnvelope envelope) {
        // Operación atómica no bloqueante:
        if (delivered.asMap().putIfAbsent(envelope.mensaje().id(), Boolean.TRUE) != null) {
            return; // Mensaje ya entregado previamente
        }

        var event = new ChatEvent(envelope.mensaje());
        for (var session : sessions.values()) {
            ...
```
Con este cambio, las verificaciones y escrituras ocurren en paralelo sin bloquear los hilos concurrentes.
