**ID:** QUA-003
**Categoría:** Calidad
**Severidad:** Alta

### Descripción
El frontend realiza llamadas de *polling* o peticiones asíncronas constantes al endpoint de notificaciones (`/api/notifications`), pero estas peticiones no están enviando correctamente el token de autorización JWT o el backend las está rechazando indebidamente.

### Pasos exactos para reproducir
1. Iniciar sesión exitosamente con un usuario válido.
2. Navegar por el feed o permanecer en la pantalla principal.
3. Observar la consola del navegador (Console) o la pestaña Network.

### Resultado esperado
Las llamadas a la API de notificaciones (HU11) deben autenticarse correctamente y devolver un estado HTTP 200 con la lista de notificaciones.

### Resultado obtenido
El endpoint devuelve constantemente un error `401 Unauthorized`, inundando la consola de errores y demostrando que el módulo de notificaciones no está integrado correctamente con la capa de seguridad JWT del usuario activo.
