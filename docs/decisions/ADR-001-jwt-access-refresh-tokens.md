# ADR-001: Esquema de Autenticación Stateless con Access Token y Refresh Token

## Estado
Aceptado

## Contexto
En la especificación original (HU02), se definió la autenticación stateless basada en JSON Web Tokens (JWT). Para entornos de producción, el uso de un único token de larga duración presenta riesgos de seguridad (ventana de exposición elevada si el token es interceptado) y una mala experiencia de usuario si el token expira abruptamente durante la navegación o el uso del chat en tiempo real.

## Decisión
Adoptar un esquema de **Doble Token (Access Token + Refresh Token)**:

1. **Access Token (JWT Stateless en Memoria):**
   - **Tiempo de vida (TTL):** 15 minutos.
   - **Formato:** JWT firmado con algoritmo RSA/ECDSA (SmallRye JWT).
   - **Claims:** `sub` (userId), `username`, `roles`, `iat`, `exp`.
   - **Almacenamiento en Cliente:** **Exclusivamente en memoria** (`tokenStore.ts` / runtime state). Queda estrictamente prohibido persistirlo en `localStorage` o `sessionStorage` para mitigar vectores de ataque XSS.
   - **Transporte:** Encabezado HTTP `Authorization: Bearer <ACCESS_TOKEN>`.

2. **Refresh Token (Rotación en Cookie HttpOnly):**
   - **Tiempo de vida (TTL):** 7 días.
   - **Formato:** Cadena criptográfica opaca generada por el backend.
   - **Almacenamiento y Transporte en Cliente:** **Cookie HTTP-Only** (`refreshToken`):
     - Atributos: `HttpOnly; Secure (en prod); SameSite=Lax; Path=/`.
     - Inaccesible por scripts en el navegador (`document.cookie`), protegiéndolo de robo por XSS.
     - `Path=/` asegura que la cookie se incluya de forma transparente en las peticiones del frontend incluso tras recargas completas del navegador (*Hard Refresh* / `Ctrl + Shift + R`).

3. **Endpoints y Ciclo de Vida:**
   - `POST /api/auth/login` y `POST /api/auth/register`: devuelven el Access Token en el cuerpo JSON y emiten la cookie `refreshToken` vía encabezado `Set-Cookie`.
   - `POST /api/auth/refresh`: consume la cookie HttpOnly de forma transparente (`withCredentials: true`), rota el token y emite un nuevo Access Token en memoria.
   - `POST /api/auth/logout`: invalida la sesión y limpia la cookie emitiendo `Max-Age=0`.

4. **Estrategia en Frontend (React):**
   - Al montar la aplicación (`initAuth`), se invoca de forma silenciosa `POST /api/auth/refresh` con `withCredentials: true`. Si la cookie está presente y válida, se hidrata el Access Token en memoria sin requerir re-login.
   - Interceptor de Axios que captura respuestas `401 Unauthorized`, solicita un nuevo token vía `/api/auth/refresh` y reintenta las peticiones encoladas.
   - Si el refresh falla (sesión expirada o revocada), se purga el estado y se redirige a login.

## Consecuencias
- **Positivas:**
  - Cumplimiento de estándares OWASP de seguridad en APIs.
  - Ventana de compromiso mínima para el Access Token.
  - Sesión persistente y transparente para el usuario final.
- **Negativas / Mitigaciones:**
  - Requiere implementar el endpoint de refresco en Quarkus y el interceptor en Axios (esfuerzo de desarrollo bajo y aislado).
