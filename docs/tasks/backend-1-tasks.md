# Tareas de Desarrollo: Backend 1 (Auth, Storage & Push)
**Módulo:** Backend 1  
**Revisión:** Tech Lead

---

## 📌 HU01: Registro de Usuarios
- [x] **Tarea 1.1:** Configurar entidad/modelo de datos `:Usuario` (Java record/DTO) con validación Jakarta Bean Validation (`@NotBlank`, `@Email`, `@Size`).
- [x] **Tarea 1.2:** Implementar servicio de hashing de contraseñas con `BCrypt` o `Argon2`.
- [x] **Tarea 1.3:** Crear `UserRepository` con driver Neo4j para verificar unicidad (`username`, `email`) y persistir el nodo `:Usuario`.
- [x] **Tarea 1.4:** Crear endpoint `POST /api/auth/register` en `AuthResource.java` retornando `201 Created` o `409 Conflict`.
- [x] **Tarea 1.5:** Pruebas unitarias y de integración para registro de usuarios.

---

## 📌 HU02: Autenticación JWT & Refresh Tokens (ADR-001)
- [x] **Tarea 2.1:** Configurar SmallRye JWT (`smallrye-jwt` y `smallrye-jwt-build`) con claves privadas/públicas RSA/ECDSA.
- [x] **Tarea 2.2:** Implementar generación de **Access Token** (15 min) con claims (`sub`, `username`, `roles`).
- [x] **Tarea 2.3:** Implementar generación y persistencia de **Refresh Token** (7 días) en Neo4j.
- [x] **Tarea 2.4:** Crear endpoint `POST /api/auth/login` validando credenciales contra Neo4j y retornando ambos tokens.
- [x] **Tarea 2.5:** Crear endpoint `POST /api/auth/refresh` para rotación de tokens.
- [x] **Tarea 2.6:** Proteger rutas REST mediante anotaciones `@RolesAllowed` / `@Authenticated`.

---

## 📌 HU06: Subida de Archivos Multimedia a RustFS (S3)
- [x] **Tarea 6.1:** Configurar cliente AWS S3 SDK v2 / Async en Quarkus apuntando a RustFS (`http://wyrdly-rustfs:9000`).
- [x] **Tarea 6.2:** Crear servicio de inicialización para asegurar la existencia del bucket `social-media-assets`.
- [x] **Tarea 6.3:** Implementar validación de archivos `multipart/form-data` (MIME types permitidos: `image/*`, `video/mp4`, límite 10 MB).
- [x] **Tarea 6.4:** Generar identificadores únicos de archivo (`storageKey` tipo UUID) y subir al bucket S3.
- [x] **Tarea 6.5:** Crear endpoint protegido `POST /api/media/upload` retornando `fileUrl` pública y metadatos.

---

## 📌 HU07: Creación y Publicación de Posts (Backend)
- [x] **Tarea 7.1:** Diseñar DTO de creación de post (`content`, `mediaUrl` opcional).
- [x] **Tarea 7.2:** Crear consulta Cypher para instanciar nodo `:Post` y crear relación `(:Usuario)-[:PUBLICA {fecha}]->(:Post)`.
- [x] **Tarea 7.3:** Crear endpoint protegido `POST /api/posts` en `PostResource.java`.
- [x] **Tarea 7.4:** Validar longitud de contenido (1-1000 caracteres) y URL de multimedia.

---

## 📌 HU11: Notificaciones Web Push (VAPID)
- [ ] **Tarea 11.1:** Generar y almacenar par de claves VAPID en configuración del backend.
- [ ] **Tarea 11.2:** Crear endpoint `GET /api/notifications/vapid-public-key` para exponer la clave pública.
- [ ] **Tarea 11.3:** Crear endpoint `POST /api/notifications/subscribe` para asociar la suscripción Web Push (`endpoint`, `p256dh`, `auth`) al nodo `:Usuario` en Neo4j.
- [ ] **Tarea 11.4:** Implementar servicio de envío de notificaciones push asíncronas con payload cifrado cuando ocurra un evento (ej. nuevo post de un seguido o nuevo mensaje).
