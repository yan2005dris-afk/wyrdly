### Estado de Réplica: ACEPTADO CON OBSERVACIONES

Agradecemos la revisión técnica del equipo auditor. Tras reproducir el flujo de despliegue inicial en un entorno limpio desde cero (Misión 1: FROM ZERO) bajo Windows 11 nativo (PowerShell y CMD), la configuración del clúster en `compose.yaml` y los scripts de aprovisionamiento, determinamos lo siguiente:

---

#### 1. Reconocimiento del problema
Se acepta la limitación de reproducibilidad reportada en el paso de preparación documentado en el `README.md`:
- El comando documentado `./scripts/generate-jwt-keys.sh ./jwt` corresponde a un script Bash POSIX que invoca utilitarios Unix (`openssl`, `chmod`).
- En terminales nativas de Windows (PowerShell y CMD) sin una llamada explícita al intérprete (`bash`), el comando no se ejecuta o falla de forma silenciosa, dejando el directorio `./jwt` vacío.
- Como consecuencia directa, al ejecutar `docker compose up -d --build`, Docker monta un directorio vacío en modo solo lectura (`${JWT_KEYS_DIR:-./jwt}:/deployments/jwt:ro`).
- Durante la inicialización del contenedor `wyrdly-backend`, la extensión `quarkus-smallrye-jwt` no encuentra el archivo configurado en `JWT_PUBLIC_KEY_LOCATION` (`file:///deployments/jwt/publicKey.pem`), lanzando la excepción:
  ```text
  SRJWT01010: File ///deployments/jwt/publicKey.pem is not found
  jakarta.enterprise.inject.spi.DeploymentException: SRJWT02003: 'mp.jwt.verify.publickey.location' is invalid
  ```
  lo cual impide que el backend supere el healthcheck y lo sitúa en un ciclo de reinicios (estado unhealthy).

Reconocemos que, bajo las pautas de la Misión 1 (FROM ZERO), cualquier requerimiento no explicitado en la documentación técnica constituye una deficiencia de preparación en el repositorio para desarrolladores o evaluadores que operen en entornos nativos de Windows.

---

#### 2. Observaciones técnicas y contextualización

1. **Evaluación de Reproducibilidad (Misión 1):**
   - El sistema no se encuentra en estado "No reproducible", sino en condición "Parcialmente reproducible": el clúster de servicios (`compose.yaml`), las bases de datos (Neo4j, Redis), el almacenamiento S3 (RustFS) y el Frontend son plenamente operativos una vez provistas las claves criptográficas (por ejemplo, invocando `bash ./scripts/generate-jwt-keys.sh ./jwt` en entornos con Git for Windows/WSL o bajo Linux/macOS).
   - El fallo radica estrictamente en la etapa de pre-configuración manual del host, no en una incompatibilidad del contenedor o del código de la aplicación.

2. **Existencia de Generador Multiplataforma en la base de código:**
   - La base de código ya incluye un generador RSA puro en Java: `com.wyrdly.JwtKeyGenerator` (`wyrdly-backend/src/main/java/com/wyrdly/JwtKeyGenerator.java`), que genera el par de claves PKCS#8 y X.509 utilizando únicamente el JDK (`java.security.KeyPairGenerator`), sin requerir OpenSSL ni Bash en el sistema operativo anfitrión.
   - No obstante, este generador estaba acoplado a la fase de pruebas de Maven (`pom.xml`) y no integrado en el flujo de arranque de Docker.

3. **Alineación con la Sección 9 de las Bases de Cátedra:**
   - La sección 9 de la declarativa exige: *“un archivo docker-compose.yml que permita levantar el entorno requerido con la menor cantidad posible de configuración manual”*.
   - El diseño ya contemplaba este enfoque para las claves Web Push (VAPID), las cuales se generan automáticamente dentro del contenedor mediante `/deployments/scripts/init-vapid-keys.sh` en el `ENTRYPOINT` del Dockerfile. Mantener la generación de JWT delegada al host como un paso manual constituyó una inconsistencia de diseño que contraviene la directriz de configuración mínima.

4. **Reclasificación de Severidad:**
   - Solicitamos reclasificar la severidad de Crítica a Media (Reproducibilidad y DX / Developer Experience).
   - El hallazgo no involucra vulnerabilidades de seguridad (OWASP), fuga de credenciales, corrupción del grafo Neo4j ni defectos en los flujos funcionales del backend. Su impacto se restringe al onboarding inicial en terminales nativas de Windows por la ausencia de un script nativo para PowerShell o de generación autónoma en el contenedor.

---

#### 3. Acciones correctivas planificadas

1. **Generación autónoma en el Entrypoint del Contenedor (Recomendación del Red Team):**
   - Modificar el `ENTRYPOINT` de `wyrdly-backend/Dockerfile` para verificar si `/deployments/jwt/publicKey.pem` existe en el volumen montado. Si está ausente, el contenedor generará automáticamente el par de llaves antes de iniciar la JVM (idéntico al flujo actual de VAPID).
   - Esto desacoplará totalmente el arranque del sistema operativo del host, logrando reproducibilidad con un único comando: `docker compose up -d --build`.

2. **Script nativo para Windows (`scripts/generate-jwt-keys.ps1`):**
   - Agregar un script nativo en PowerShell que utilice las librerías criptográficas de .NET (`[System.Security.Cryptography.RSA]`) para aquellos operadores que deseen rotar o inspeccionar las claves en el host sin dependencias externas.

3. **Actualización de Documentación (`README.md` y `LOCAL_DEPLOYMENT.md`):**
   - Actualizar las instrucciones rápidas especificando la sintaxis adecuada para PowerShell, CMD y Bash mientras se publica la automatización en el contenedor.
