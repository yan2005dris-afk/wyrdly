**ID:** SEC-001
**Categoría:** Seguridad / Requisitos
**Severidad:** Alta

### Descripción
El documento de arquitectura y el README afirman que existe un "Rate Limiting: 30 reacciones/min/usuario" gestionado por Redis para la HU09. Sin embargo, al realizar pruebas de estrés en la interfaz interactuando rápidamente con el botón de "Me gusta", el sistema procesa todas las peticiones exitosamente (HTTP 200 OK) sin aplicar ningún bloqueo o límite.

### Pasos exactos para reproducir
1. Iniciar sesión y navegar al Feed.
2. Abrir las herramientas de desarrollador (F12) en la pestaña Network.
3. Hacer clic repetidamente y a gran velocidad (más de 35 veces) sobre el botón de reacción de un post.
4. Observar las respuestas de la API en la red.

### Resultado esperado
A partir de la petición número 31 en menos de un minuto, la API debería rechazar la solicitud devolviendo un error `429 Too Many Requests`.

### Resultado obtenido
Todas las peticiones a la ruta `/react` devuelven `200 OK`. El mecanismo de Rate Limiting documentado no existe, está mal configurado o falla al abrir (Fail Open) permitiendo el abuso del endpoint.
