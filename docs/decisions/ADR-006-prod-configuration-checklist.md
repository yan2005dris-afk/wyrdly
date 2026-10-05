# ADR-006: Checklist de Configuración para Producción

## Estado
Aceptado

## Contexto
Este documento recoge todas las variables de entorno, decisiones de configuración y consideraciones de seguridad necesarias para un despliegue correcto de Wyrdly en producción (HTTPS con Cloudflare Tunnel + Nginx + Quarkus backend + RustFS/S3 + Neo4j + Redis).

---

## Variables de Entorno Requeridas en Producción

### Neo4j
| Variable | Default Compose | Notas |
|---|---|---|
| `NEO4J_URI` | `bolt://wyrdly-neo4j:7687` | Usar hostname del contenedor en la misma red |
| `NEO4J_USERNAME` | `neo4j` | |
| `NEO4J_PASSWORD` | *(sin default — requerido)* | Usar secreto fuerte, nunca `password` |
| `NEO4J_MAX_POOL_SIZE` | `300` | Ajustar según carga esperada |

### RustFS / S3
| Variable | Default Compose | Notas |
|---|---|---|
| `RUSTFS_ENDPOINT` | `http://wyrdly-rustfs:9000` | En AWS S3 usar `https://s3.<region>.amazonaws.com` |
| `RUSTFS_ACCESS_KEY` | *(sin default — requerido)* | Nunca `placeholder_key` en prod |
| `RUSTFS_SECRET_KEY` | *(sin default — requerido)* | Nunca `placeholder_secret` en prod |
| `RUSTFS_BUCKET` | `social-media-assets` | |
| `RUSTFS_REGION` | `us-east-1` | Ajustar si se usa AWS S3 en otra región |
| `RUSTFS_FORCE_PATH_STYLE` | `true` | `true` para RustFS/MinIO; `false` para AWS S3 en prod |

### Autenticación JWT
| Variable | Default Compose | Notas |
|---|---|---|
| `JWT_ISSUER` | `https://wyrdly.com/issuer` | Debe coincidir con el claim `iss` de los tokens emitidos |
| `JWT_PUBLIC_KEY_LOCATION` | `/deployments/jwt/publicKey.pem` | Montado vía volumen `:ro` |
| `JWT_PRIVATE_KEY_LOCATION` | `/deployments/jwt/privateKey.pem` | Montado vía volumen `:ro` |
| `JWT_KEYS_DIR` | `./jwt` | Directorio local con las claves PEM generadas |
| `AUTH_REFRESH_TOKEN_MAX_AGE` | `604800` (7 días) | En segundos |

### Cookies de Sesión
| Variable | Default Prod | Notas |
|---|---|---|
| `AUTH_COOKIE_SECURE` | `true` | **Crítico:** debe ser `true` en producción (HTTPS). El default en Compose ya lo impone. |
| `AUTH_COOKIE_SAME_SITE` | `LAX` | Usar `LAX` con Cloudflare Tunnel. Si frontend y backend son cross-site, evaluar `NONE` (requiere `Secure=true`). |

### CORS
| Variable | Default Compose | Notas |
|---|---|---|
| `CORS_ORIGINS` | `http://localhost:3000,...,https://wyrdly.yandro.tech` | Listar **exactamente** los orígenes del frontend. Con `withCredentials=true` no se permite `*`. |

### Redis
| Variable | Default Compose | Notas |
|---|---|---|
| `REDIS_HOSTS` | `redis://wyrdly-redis:6379` | Usar hostname del contenedor en la red interna |
| `REDIS_PORT` | `6379` | Solo para exposición de puerto host; no cambia la config del backend |

### Web Push VAPID (HU11)
| Variable | Default Compose | Notas |
|---|---|---|
| `VAPID_SUBJECT` | `mailto:ops@wyrdly.com` | Actualizar con el email de ops real |
| `VAPID_PUBLIC_KEY_LOCATION` | `/deployments/vapid/publicKey.txt` | Generado por `init-vapid-keys.sh` |
| `VAPID_PRIVATE_KEY_LOCATION` | `/deployments/vapid/privateKey.txt` | |
| `VAPID_GENERATE_IF_MISSING` | `true` | Genera el keypair si no existe; idempotente |
| `VAPID_KEYS_DIR` | `./vapid` | Directorio local persistido entre reinicios |

### Cloudflare Tunnel
| Variable | Default Compose | Notas |
|---|---|---|
| `CLOUDFLARE_TUNNEL_TOKEN` | *(sin default — requerido)* | Token de `cloudflared tunnel create` |

### Build del Frontend
| Variable | Valor en Compose | Notas |
|---|---|---|
| `VITE_API_BASE_URL` | `""` (vacío) | Correcto para producción: el navegador usa rutas relativas (`/api/...`) a través del Nginx del frontend. **No cambiar** a menos que el frontend se sirva desde un dominio diferente al de la API. |

---

## Consideraciones de Arquitectura y Seguridad

### Reverse Proxy Chain

```
Browser → Cloudflare Tunnel → wyrdly-frontend (Nginx:8080) → wyrdly-backend (Quarkus:8080)
```

- **Nginx** propaga `X-Forwarded-For`, `X-Forwarded-Proto`, `X-Forwarded-Host` y `X-Real-IP`.
- **Quarkus** consume estos headers vía `quarkus.http.proxy.proxy-address-forwarding=true` y `quarkus.http.proxy.allow-x-forwarded=true`.
- Esto es necesario para que `app.public-base-url` sea correcto y las cookies `Secure` funcionen cuando Quarkus detecta que la petición llegó originalmente por HTTPS.

### Subida de Multimedia (Estado Actual vs ADR-003)

> [!NOTE]
> El ADR-003 planifica una migración a Presigned URLs directas (browser → S3). El código actual usa subida **a través del backend** (`POST /api/media/upload` con multipart). S3/RustFS **no necesita CORS hacia el browser** en el estado actual.
>
> Si se implementa el patrón Presigned URL en el futuro, agregar la siguiente política CORS al bucket `social-media-assets` en RustFS:
> ```xml
> <CORSConfiguration>
>   <CORSRule>
>     <AllowedOrigin>https://wyrdly.yandro.tech</AllowedOrigin>
>     <AllowedMethod>PUT</AllowedMethod>
>     <AllowedHeader>Content-Type</AllowedHeader>
>     <MaxAgeSeconds>3600</MaxAgeSeconds>
>   </CORSRule>
> </CORSConfiguration>
> ```

### Puertos Expuestos al Host (Revisar en Producción)

En producción solo el frontend debe estar accesible desde el exterior vía Cloudflare Tunnel. Evaluar **eliminar** los `ports` en `compose.yaml` de los servicios internos para no exponerlos directamente al host:

| Servicio | Puerto Expuesto | Acción Recomendada |
|---|---|---|
| `wyrdly-neo4j` | `7474`, `7687` | Eliminar en prod (acceso solo vía red interna) |
| `wyrdly-rustfs` | `9000`, `9001` | Eliminar en prod (acceso solo vía red interna) |
| `wyrdly-redis` | `6379` | Eliminar en prod |
| `wyrdly-backend` | `8080` | Eliminar en prod (Nginx usa red interna) |
| `wyrdly-frontend` | `3000→8080` | Mantener solo si Cloudflare apunta acá |

### Claves JWT

Generar con `scripts/generate-jwt-keys.sh`. Las claves deben existir en `$JWT_KEYS_DIR` antes del primer `docker compose up`. El volumen se monta como `:ro` (read-only).

---

## Consecuencias
- **Positivas:** Configuración explícita y auditada por entorno. Las cookies de sesión son seguras en HTTPS. El proxy chain es transparente y Quarkus construye URLs y responde cookies correctamente.
- **Negativas / A Revisar:** Los puertos internos siguen expuestos al host en el `compose.yaml` actual. Se recomienda revisarlos antes del primer despliegue en un servidor público.
