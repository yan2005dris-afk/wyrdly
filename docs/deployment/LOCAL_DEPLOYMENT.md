# Guía de Despliegue Local — Wyrdly

Esta guía detalla paso a paso cómo desplegar y ejecutar **Wyrdly** en un entorno local. Wyrdly es una plataforma distribuida que consta de un backend en **Quarkus (Java 21)**, base de datos de grafos **Neo4j 5.x**, almacenamiento de objetos compatible con S3 **RustFS**, caché en memoria **Redis 7** y frontend en **React (Vite + TypeScript + Tailwind CSS)**.

---

## 📋 Requisitos Previos

Dependiendo del método de despliegue que elijas, necesitarás las siguientes herramientas instaladas en tu sistema:

### Para Despliegue Completo con Docker (Recomendado)
- **Docker Engine 24+** y **Docker Compose v2.20+** (o Docker Desktop en Windows/macOS/Linux).
- **OpenSSL** y **Bash** (para scripts de generación de claves criptográficas).
- Mínimo **4 GB de RAM** libre para contenedores.

### Para Modo Desarrollo Híbrido (Live Coding)
- **Java 21 LTS** (Eclipse Temurin, OpenJDK o GraalVM).
- **Node.js 20+** o **Node.js 22+**.
- **pnpm 9+** (habilitar con `corepack enable && corepack prepare pnpm@latest --activate`).
- **Maven 3.9+** (el proyecto incluye el wrapper `./mvnw`, no requiere instalación global).

---

## 🏗️ Topología de Servicios y Puertos Locales

| Servicio | Contenedor | Puerto Host | Descripción |
| :--- | :--- | :--- | :--- |
| **Frontend** | `wyrdly-frontend` | `3000` | SPA React servida por Nginx con reverse proxy hacia la API |
| **Backend API** | `wyrdly-backend` | `8080` | Quarkus REST, WebSockets `/ws/chat`, Dev UI en `/q/dev` |
| **Neo4j Browser** | `wyrdly-neo4j` | `7474` | Consola Web interactiva de Neo4j |
| **Neo4j Bolt** | `wyrdly-neo4j` | `7687` | Protocolo binario Bolt para el driver de base de datos |
| **RustFS S3** | `wyrdly-rustfs` | `9000` | API S3 para subida y descarga de avatares/multimedia |
| **RustFS Console**| `wyrdly-rustfs` | `9001` | Consola Web de administración de buckets S3 |
| **Redis** | `wyrdly-redis` | `6379` | Idempotencia, rate limiting y caché |

---

## 🚀 Método 1: Despliegue Completo con Docker Compose (Recomendado)

Este método levanta toda la infraestructura distribuida en contenedores aislados con healthchecks y redes compartidas.

### Paso 1: Clonar el Repositorio
```bash
git clone https://github.com/Gentleman-Programming/yaga-social.git
cd yaga-social
```

### Paso 2: Configurar las Variables de Entorno
Copia el archivo de plantilla `.env.example` a `.env`:
```bash
cp .env.example .env
```

Para uso local, los valores por defecto en `.env.example` son suficientes, pero debes definir o ajustar las siguientes variables mínimas en `.env`:
```env
# Contraseñas y accesos locales
NEO4J_PASSWORD=secretpassword
RUSTFS_ACCESS_KEY=rustfsadmin
RUSTFS_SECRET_KEY=rustfssecret123

# En local sin HTTPS, asegurar cookies no restrictivas:
AUTH_COOKIE_SECURE=false

# Habilitar precarga de datos de prueba:
WYRDLY_SEED_ENABLED=true
```

> [!TIP]
> Si no vas a exponer la aplicación mediante Cloudflare Tunnel en local, asigna un valor dummy a `CLOUDFLARE_TUNNEL_TOKEN=dummy_token` o comenta el servicio `wyrdly-cloudflared` en `compose.yaml`.

### Paso 3: Generar las Claves Criptográficas

1. **Par de claves RSA para SmallRye JWT (Tokens de acceso):**
   Ejecuta el script incluido en el repositorio para generar el par de claves en `./jwt`:
   ```bash
   ./scripts/generate-jwt-keys.sh ./jwt
   ```
   *Esto generará `privateKey.pem` y `publicKey.pem` requeridos por el backend.*

2. **Par de claves ECDSA P-256 para Web Push VAPID:**
   El contenedor `wyrdly-backend` ejecuta automáticamente el script idempotente `init-vapid-keys.sh` al iniciar y persiste las claves en `./vapid`. No requiere acción manual.

### Paso 4: Construir y Levantar los Contenedores
Inicia todos los servicios en segundo plano:
```bash
docker compose up -d --build
```

### Paso 5: Verificar el Estado de Salud
Monitorea los servicios hasta que todos figuren como `healthy`:
```bash
docker compose ps
```

Puedes ver los logs en tiempo real:
```bash
# Ver todos los logs
docker compose logs -f

# Ver sólo logs del backend
docker compose logs -f wyrdly-backend
```

### Paso 6: Acceder a los Servicios
- **Aplicación Web (Frontend):** [http://localhost:3000](http://localhost:3000)
- **Backend API (Quarkus):** [http://localhost:8080/q/health](http://localhost:8080/q/health)
- **Neo4j Browser:** [http://localhost:7474](http://localhost:7474) (Usuario: `neo4j` / Contraseña: la que definiste en `NEO4J_PASSWORD`)
- **RustFS Web Console:** [http://localhost:9001](http://localhost:9001) (Credenciales: `RUSTFS_ACCESS_KEY` / `RUSTFS_SECRET_KEY`)

---

## 💻 Método 2: Modo Desarrollo Híbrido (Live Coding / Hot Reload)

Para iterar rápidamente en el código fuente con recarga en vivo de Quarkus y Vite HMR:

### Paso 1: Levantar Únicamente la Infraestructura Base
Inicia Neo4j, RustFS y Redis mediante Docker Compose:
```bash
docker compose up -d wyrdly-neo4j wyrdly-rustfs wyrdly-redis
```

### Paso 2: Generar Claves JWT para el Backend Local
```bash
./scripts/generate-jwt-keys.sh wyrdly-backend/src/main/resources/jwt
```

### Paso 3: Iniciar el Backend en Modo Dev (Quarkus)
En una terminal:
```bash
cd wyrdly-backend
./mvnw quarkus:dev
```
- El backend compilará y se levantará en `http://localhost:8080`.
- **Dev UI interactivo:** [http://localhost:8080/q/dev/](http://localhost:8080/q/dev/)
- Cualquier cambio en archivos Java o propiedades se recargará automáticamente al hacer una petición.

### Paso 4: Iniciar el Frontend en Modo Dev (Vite)
En otra terminal:
```bash
cd wyrdly-frontend
pnpm install
pnpm run dev
```
- El servidor de desarrollo de Vite estará disponible en `http://localhost:5173`.
- En modo desarrollo, el frontend apunta automáticamente a `http://localhost:8080`.

---

## 👤 Usuarios de Prueba Pre-cargados (Seed Data)

Si `WYRDLY_SEED_ENABLED=true` (activo por defecto en modo desarrollo), el sistema ejecuta automáticamente `neo4j/seeds/seed.cypher` y precarga los siguientes usuarios del equipo con grafo de seguimiento y posts:

| Usuario (`username`) | Email | Contraseña | Rol / Descripción |
| :--- | :--- | :--- | :--- |
| `yandris` | `yandris@wyrdly.social` | `Password123!` | Tech Lead & Arquitectura |
| `gino` | `gino@wyrdly.social` | `Password123!` | Backend S3 & Push |
| `andy` | `andy@wyrdly.social` | `Password123!` | Backend Cypher & WebSockets |
| `allison` | `allison@wyrdly.social` | `Password123!` | Frontend & UI/UX |
| `marcus` | `marcus@wyrdly.social` | `Password123!` | Usuario de prueba sugerencias FoF |

---

## 🧪 Verificación y Suite de Pruebas (CI Local)

Para validar que todo el código cumple con formato, linters y tests unitarios antes de hacer commits:

```bash
# Ejecutar suite completa local (formato + linter + tests backend y frontend)
./scripts/ci-local.sh

# Ejecutar con auto-formateo si hay fallos de estilo
./scripts/ci-local.sh --fix

# Solo tests de backend
cd wyrdly-backend && ./mvnw test

# Solo tests de frontend
cd wyrdly-frontend && pnpm run test
```

---

## 🛑 Detener y Limpiar el Entorno

### Detener contenedores manteniendo datos
```bash
docker compose down
```

### Detener y borrar volúmenes (Reinicio desde cero)
```bash
docker compose down -v
```

---

## ❓ Preguntas Frecuentes y Solución de Problemas

1. **Error: `connection refused` a Neo4j al arrancar el backend en local:**
   - Verifica que el contenedor `wyrdly-neo4j` esté en estado `healthy` (`docker compose ps`). Neo4j tarda unos 20-30 segundos en inicializar la base de datos y plugins APOC.
2. **Error al generar claves JWT:**
   - Asegúrate de tener instalado `openssl` en tu sistema (`which openssl`).
3. **Error 401 en login con cookies en modo local:**
   - Asegúrate de tener `AUTH_COOKIE_SECURE=false` en tu archivo `.env` local si estás accediendo por HTTP (`http://localhost:3000`). En producción sobre HTTPS este valor debe ser `true`.
