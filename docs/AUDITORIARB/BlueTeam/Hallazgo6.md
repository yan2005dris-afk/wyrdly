**ID:** RES-005
**Categoría:** Resiliencia (Chaos Mode)
**Severidad:** Alta

### Descripción
Durante la Misión 6 (Chaos Mode), se simuló la caída de la base de datos principal deteniendo el contenedor `wyrdly-neo4j`. El sistema demostró no tener mecanismos de degradación elegante ni un manejo de excepciones adecuado en la capa de la API, resultando en una experiencia de usuario bloqueante y confusa.

### Pasos exactos para reproducir
1. Iniciar el entorno completo de Wyrdly.
2. Detener la base de datos: `docker stop wyrdly-neo4j`.
3. Recargar el navegador o intentar iniciar sesión en el frontend.

### Resultado esperado
El frontend debería capturar el error de indisponibilidad y mostrar un mensaje amigable al usuario (ej. "Servicio temporalmente en mantenimiento").

### Resultado obtenido
La interfaz entra en estados de carga infinita. La API responde con códigos HTTP 500 (Internal Server Error) en las rutas de `/auth/login` y `/auth/refresh`. Al intentar interactuar, el estado de autenticación se corrompe, arrojando mensajes contradictorios al usuario como "Invalid username or password" seguido de "Email already registered" cuando se intenta reactivar el flujo.
