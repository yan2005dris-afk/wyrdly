# Guía de Despliegue en Producción — Wyrdly

Esta guía cubre el despliegue de **Wyrdly** sobre un **self-hosted runner de GitHub Actions** que arranca toda la plataforma con `docker compose`. El contrato de configuración vive en [`.env.prod`](../../.env.prod).

> 📖 **Para desarrollo local** consultá [`LOCAL_DEPLOYMENT.md`](./LOCAL_DEPLOYMENT.md) — usa una copia de `.env.dev` y es deliberadamente permisiva.

---

## 📋 Requisitos Previos del Host

- **Docker Engine 24+** y **Docker Compose v2.20+**.
- **OpenSSL** (para generar claves RSA JWT y VAPID).
- Mínimo **4 GB de RAM** libre (recomendado 8 GB para `prod` real).
- Acceso de salida a internet para pull de imágenes.
- DNS de tu dominio apuntando al tunnel de Cloudflare (si lo usás).
- Un **self-hosted runner de GitHub Actions** con los labels `[self-hosted, wyrdly]` registrado en el repo.

---

## 🗂️ Layout del Servidor

```
/etc/wyrdly/
├── .env                          # generado a partir de .env.prod
├── jwt/
│   ├── privateKey.pem            # RSA 2048 PKCS8
│   └── publicKey.pem
└── vapid/
    ├── privateKey.txt            # ECDSA P-256, base64url
    └── publicKey.txt
```

`/etc/wyrdly/.env` está fuera del checkout (no se commitea nunca). Las claves se generan una sola vez.

---

## 🚀 Despliegue Paso a Paso

### Paso 1 — Generar Claves Criptográficas

```bash
sudo mkdir -p /etc/wyrdly/jwt /etc/wyrdly/vapid
sudo chmod 750 /etc/wyrdly/jwt /etc/wyrdly/vapid

# Claves RSA 2048 para SmallRye JWT (tokens de acceso).
sudo /opt/wyrdly/scripts/generate-jwt-keys.sh /etc/wyrdly/jwt
sudo chmod 644 /etc/wyrdly/jwt/*.pem

# Claves ECDSA P-256 para Web Push VAPID.
# Si ya tenés un script generate-vapid-keys.sh, úsalo. Si no:
sudo openssl ecparam -name prime256v1 -genkey -noout -out /tmp/vapid_priv.pem
sudo openssl pkcs8 -topk8 -nocrypt -in /tmp/vapid_priv.pem -out /etc/wyrdly/vapid/privateKey.txt
sudo openssl ec -in /tmp/vapid_priv.pem -pubout -out /etc/wyrdly/vapid/publicKey.txt 2>/dev/null
sudo rm -f /tmp/vapid_priv.pem
```

### Paso 2 — Crear el `.env` de Producción

```bash
sudo cp /opt/wyrdly/.env.prod /etc/wyrdly/.env
sudo chmod 600 /etc/wyrdly/.env
sudo $EDITOR /etc/wyrdly/.env
```

Reemplazá cada `__SET_ME_*__` con el valor real. Las **mínimas obligatorias** son:

| Variable | Generador sugerido |
| :--- | :--- |
| `NEO4J_PASSWORD` | `openssl rand -hex 24` (48 hex chars) |
| `RUSTFS_ACCESS_KEY` | `openssl rand -hex 16` (32 hex chars) |
| `RUSTFS_SECRET_KEY` | `openssl rand -hex 32` (64 hex chars) |
| `CLOUDFLARE_TUNNEL_TOKEN` | Dashboard Cloudflare → tu tunnel → `Configure` |

`CORS_ORIGINS` y `APP_PUBLIC_BASE_URL` deben matchear tu dominio público.

> [!IMPORTANT]
> **Generadores URL-safe solamente.** Usá `openssl rand -hex N`, nunca `openssl rand -base64 N`.
> El alfabeto de base64 estándar (`A-Za-z0-9+/=`) incluye `+` y `/`, que rompen el parseo de `NEO4J_AUTH`
> (Neo4j hace `split('/')` en el entrypoint y rechaza cualquier resto) y son frágiles para secretos binarios
> consumidos por clientes S3 / JSON / URLs. El bug que motivó esta nota está documentado en
> `odd/tasks/fix-neo4j-auth-password-format.md`.
>
> **Rotar password ≠ cambiar el `.env`.** Neo4j persiste el auth en el volumen de datos: con volumen ya creado,
> cambiar `NEO4J_PASSWORD` no tiene efecto y el container sigue fallando igual. En dev: `docker compose down`,
> `docker volume rm wyrdly_neo4j_data`, `up -d` (el seed se re-ejecuta solo). En prod con datos reales: rotación
> online (`neo4j-admin dbms set-initial-password` o `ALTER CURRENT USER SET PASSWORD`), nunca wipe.

### Paso 3 — Verificar el Contrato

Antes de hacer `docker compose up`, validá la configuración:

```bash
cd /opt/wyrdly
docker compose --env-file /etc/wyrdly/.env config > /dev/null && echo OK
```

Si `compose config` falla con un error `required variable YYYY is required`, falta una variable obligatoria en tu `.env`.

### Paso 4 — Disparar el Deploy

Atajo equivalente en el host (mismo env-file, misma validación, más smoke local):
`./scripts/prod-up.sh`. El deploy corre en el self-hosted runner vía GitHub Actions:

- Push a `develop` o `main`, o
- `Actions → Deploy Wyrdly → Run workflow`.

El workflow:
1. Lee `/etc/wyrdly/.env`.
2. **Valida que las vars obligatorias estén presentes** (fallo temprano).
3. Asegura que las claves RSA existan.
4. Ejecuta `docker compose --env-file /etc/wyrdly/.env up -d --build --remove-orphans`.
5. Muestra el estado y prune de imágenes viejas.

### Paso 5 — Smoke Test Post-Deploy

```bash
# Frontend healthcheck (entry point público, vía Cloudflare)
curl -fsS https://wyrdly.yandro.tech/healthz   # nginx → "healthy"

# Frontend healthcheck interno (loopback, :8080)
curl -fsS http://127.0.0.1:8080/healthz

# Backend healthcheck interno (loopback, :18080 — NO público).
# El frontend Nginx NO expone /q/* hacia el backend, así que esta ruta
# sólo es accesible desde el host (o vía SSH tunnel). Para healthcheck
# público usá /healthz del frontend (arriba) — Nginx devuelve 200 si el
# container responde, y la app entera está healthy porque depende_on es
# service_healthy contra wyrdly-backend.
curl -fsS http://127.0.0.1:18080/q/health/live
# → "status: UP"

# Logs en vivo
docker compose --env-file /etc/wyrdly/.env logs -f wyrdly-backend
```

> ⚠️ **Mapeo de puertos**: ambos servicios escuchan en el container port 8080,
> pero en el host usan puertos distintos. El frontend ocupa el **8080**
> (entrada pública de Cloudflare Tunnel). El backend ocupa el **18080**
> (sólo loopback, para ops y smoke tests). El Nginx del frontend hace
> reverse proxy de `/api` y `/ws` al backend por la red interna de Compose
> (`wyrdly-backend:8080`), no por el puerto host.

---

## 🔐 Checklist de Hardening Pre-Deploy

- [ ] `/etc/wyrdly/.env` con `chmod 600` y owner `root:root` (o `deploy:deploy`).
- [ ] `CORS_ORIGINS` **no** incluye localhost.
- [ ] `AUTH_COOKIE_SECURE=true`, `AUTH_COOKIE_SAME_SITE=Lax`.
- [ ] `WYRDLY_SEED_ENABLED=false`.
- [ ] `VAPID_GENERATE_IF_MISSING=false` (claves pre-aprovisionadas).
- [ ] Neo4j Browser (`:7474`) **no** expuesto públicamente — sólo accesible por SSH tunnel si lo necesitás (Neo4j 5 community no tiene flag para apagarlo, se restringe por firewall / bind address).
- [ ] `BACKEND_BIND=127.0.0.1` y `FRONTEND_BIND=127.0.0.1` (sólo cloudflared es público).
- [ ] `BACKEND_PORT=18080` y `FRONTEND_PORT=8080` (no chocan en el mismo host).
- [ ] Cloudflare Tunnel con token real.
- [ ] DNS de `wyrdly.yandro.tech` apunta al tunnel.
- [ ] Las claves RSA y VAPID se respaldan en un lugar seguro (1Password, Vault, etc.).

---

## 🛑 Rollback

```bash
cd /opt/wyrdly

# Ver qué hay corriendo
docker compose --env-file /etc/wyrdly/.env ps

# Bajar a una imagen anterior (etiqueta explícita)
docker compose --env-file /etc/wyrdly/.env pull
docker compose --env-file /etc/wyrdly/.env up -d --no-build <service>

# Si querés volver al commit anterior
git checkout <previous-commit>
docker compose --env-file /etc/wyrdly/.env up -d --build
```

---

## 🧰 Troubleshooting

| Síntoma | Causa probable | Acción |
| :--- | :--- | :--- |
| `compose config` falla con `required variable X` | Falta X en `/etc/wyrdly/.env` | Editar el `.env` |
| Login devuelve 401 con `Secure` cookie en HTTP | `AUTH_COOKIE_SECURE=true` con HTTP | Activá HTTPS (Cloudflare) o usá solo dev |
| Neo4j Browser no carga | `NEO4J_BROWSER_ENABLED=false` | Acceso local vía SSH tunnel si lo necesitás |
| Backend no arranca | JWT keys ausentes | Verificar `/etc/wyrdly/jwt/*.pem` |
| Neo4j en restart loop: `Invalid value for NEO4J_AUTH` | Password base64 con `/`/`+`, o password rotada sin tratar el volumen | Regenerar hex y recrear el volumen (dev) o rotación online (prod con datos, ver nota) |
| VAPID no firma push | Claves ECDSA mal generadas | Regenerar con el script |

---

## 📚 Referencias

- [`.env.prod`](../../.env.prod) — contrato completo de vars.
- [`.env.dev`](../../.env.dev) — quick-start local.
- [`compose.yaml`](../../compose.yaml) — defaults prod-safe.
- [`application.properties`](../../wyrdly-backend/src/main/resources/application.properties) — bloque `%prod.`.
- [`scripts/generate-jwt-keys.sh`](../../scripts/generate-jwt-keys.sh) — provisionar RSA.
- [`scripts/generate-vapid-keys.sh`](../../scripts/generate-vapid-keys.sh) — provisionar VAPID (idempotente).
- [`LOCAL_DEPLOYMENT.md`](./LOCAL_DEPLOYMENT.md) — desarrollo local.
- [Runbook Web Push (VAPID)](../operations/WEB_PUSH.md) — rotación de claves VAPID.