# ADR-003: Subida de Archivos y Binarios a RustFS/S3 mediante Presigned URLs y Staging Pattern

## Estado
Aceptado

## Contexto
El sistema requiere soporte para la subida de imágenes y archivos multimedia (fotos de perfil, avatars, adjuntos en posts).
El almacenamiento de objetos está provisto por **RustFS** (servicio compatible con la API S3 de AWS, puerto 9000).

Tradicionalmente, las aplicaciones web manejan subidas enviando `multipart/form-data` directamente al servidor de backend. Sin embargo, en arquitecturas orientadas a alto rendimiento, microservicios y Clean Architecture:
1. Transferir binarios pesados a través del servidor Quarkus satura la memoria heap (buffers de bytes) y el pool de hilos de I/O de la API REST.
2. Mezclar la persistencia de metadatos (Neo4j) con la transferencia de archivos binarios en una sola petición no proporciona atomicidad transaccional real (no existe 2PC entre S3 y Neo4j), creando la ilusión de atomicidad mientras se pagan los costos de rendimiento.

## Decisión
Adoptar el patrón **Direct-to-S3 con Presigned URLs y Reservation/Staging**:

1. **Flujo de Carga (Direct to Storage):**
   - El cliente solicita al backend una URL prefirmada para subida:
     - `POST /api/media/upload-url`
     - **Request:** `{ "filename": "avatar.jpg", "contentType": "image/jpeg", "category": "avatar" }`
     - **Response 200 OK:** `{ "uploadUrl": "http://.../social-media-assets/staging/avatars/...", "key": "staging/avatars/<UUID>.jpg", "expiresIn": 900 }`
   - El cliente realiza un `PUT` binario directo a RustFS/S3 utilizando la `uploadUrl` obtenida.

2. **Commit Atómico de Metadatos (Backend / Neo4j):**
   - Una vez confirmada la subida por S3 (HTTP 200), el cliente envía la `key` de staging al actualizar la entidad (ej. `PUT /api/users/profile` con `{ "avatarKey": "staging/avatars/<UUID>.jpg" }`).
   - El backend valida la existencia del objeto en RustFS/S3, lo promociona a su ruta definitiva (`avatars/<USER_ID>.jpg`) y actualiza el grafo en Neo4j de forma transaccional.

3. **Limpieza Automática y Recolección de Basura (S3 Lifecycle):**
   - El bucket de RustFS/S3 define una regla de ciclo de vida (Lifecycle Rule) para expirar y eliminar automáticamente objetos en el prefijo `staging/` tras 24 horas.
   - Si una subida se interrumpe o el usuario descarta los cambios, el storage se autolimpia sin requerir jobs cron o lógica de compensación manual en el backend.

## Consecuencias
- **Positivas:**
  - **Eficiencia y escalabilidad:** Quarkus no procesa payloads binarios; el tráfico de archivos viaja directo entre el navegador y RustFS/S3.
  - **Consistencia en metadatos:** Neo4j solo referencia archivos efectivamente subidos y validados.
  - **Manejo de huérfanos sin sobrecarga:** Las subidas no confirmadas son eliminadas pasivamente por el ciclo de vida del bucket.
- **Negativas / Mitigaciones:**
  - Requiere que el frontend gestione el flujo en dos pasos (obtener URL firmada -> subir binario -> confirmar metadata).
  - RustFS/S3 debe tener habilitados encabezados CORS adecuados para peticiones `PUT` directas desde el navegador.
