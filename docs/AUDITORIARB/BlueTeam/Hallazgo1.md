**ID:** REP-001
**Categoría:** Reproducibilidad
**Severidad:** Crítica

### Descripción
El contenedor `wyrdly-backend` entra en estado *unhealthy* y detiene la orquestación del sistema mediante Docker Compose. Esto ocurre porque el paso de preparación documentado en el `README.md` utiliza un script Bash (`.sh`) para la generación de llaves JWT, el cual falla de manera silenciosa o no es reconocido en entornos nativos de Windows (PowerShell/CMD).

### Pasos exactos para reproducir
1. Clonar el repositorio en un sistema operativo Windows.
2. Abrir una terminal nativa (PowerShell o CMD).
3. Ejecutar los comandos indicados en la documentación:
   ```powershell
   cp .env.dev .env
   ./scripts/generate-jwt-keys.sh ./jwt

### Ejecutar el despliegue del cluster:
   ```powershell
docker compose up -d --build
   ```

Revisar el estado de los contenedores mediante docker ps y observar el crash loop en los logs del backend usando docker logs wyrdly-backend.

### Resultado esperado
Los scripts de configuración preparan los recursos necesarios en el sistema anfitrión y todos los contenedores levantan correctamente, exponiendo la API y el Frontend sin errores críticos de inicio.

### Resultado obtenido
El script Bash no se ejecuta correctamente en Windows, por lo que la carpeta jwt/ queda vacía. Al levantar el contenedor, Quarkus falla al inicializar el mecanismo de autenticación por la ausencia del archivo publicKey.pem, lanzando un error 500 y deteniendo el servicio.

Evidencia
Logs extraídos del contenedor wyrdly-backend durante el intento de arranque:
   ```powershell
2026-10-11 04:17:30,130 DEBUG [io.smallrye.jwt.util] (vert.x-eventloop-thread-1) SRJWT01010: File ///deployments/jwt/publicKey.pem is not found
2026-10-11 04:17:30,131 DEBUG [io.vertx.ext.web.RoutingContext] (vert.x-eventloop-thread-1) RoutingContext failure (500): jakarta.enterprise.inject.spi.DeploymentException: SRJWT02003: 'mp.jwt.verify.publickey.location' is invalid
   ```
### Impacto
El proyecto carece de "Reproducibilidad From Zero" para desarrolladores o auditores que operen fuera del ecosistema Linux/Mac. Este fallo bloquea el despliegue íntegro del sistema, impidiendo cualquier tipo de evaluación o desarrollo posterior.

### Recomendación
* Proveer un script equivalente para entornos Windows (por ejemplo, generate-jwt-keys.ps1 o .bat).

* Modificar el README.md para especificar explícitamente el uso obligatorio de herramientas como Git Bash o WSL para usuarios de Windows.

* Alternativamente, delegar la generación de las llaves al propio Docker (ej. mediante un contenedor efímero o un paso de entrypoint) para garantizar la total independencia del sistema operativo anfitrión.
