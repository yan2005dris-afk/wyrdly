# Demo Video Script — Wyrdly Social Network (HU05-HU09)
**Duración: 3-5 minutos**

---

## 📋 Estructura del Video

### Intro (30s)
```
"Wyrdly es una red social distribuida construida con Quarkus, Neo4j 
y React. Vamos a mostrar 5 historias de usuario completamente implementadas 
y probadas en producción."
```

---

## ✅ PARTE 1: Pruebas Backend (45s)
**Tiempo: 0:30 - 1:15**

### Comando a ejecutar:
```bash
cd wyrdly-backend
./mvnw test -DskipITs=false
```

**Narración durante ejecución:**
```
"134 tests backend ejecutando. Cobertura completa en:
- Persistencia Neo4j (reacciones, feed, usuarios)
- Validación de transacciones
- Integridad de datos distribuidos
- Rate limiting y idempotencia"
```

**Output esperado:**
```
BUILD SUCCESS
Tests run: 134, Failures: 0, Errors: 0, Skipped: 0
```

---

## 🎨 PARTE 2: Pruebas Frontend (45s)
**Tiempo: 1:15 - 2:00**

### Comando a ejecutar:
```bash
cd wyrdly-frontend
npm test -- --passWithNoTests --coverage=false
```

**Narración:**
```
"244 tests frontend pasando. Componentes validados:
- Hooks de reacción (optimistic updates)
- Feed con scroll infinito
- Integración con REST API
- Manejo de errores y rate limiting"
```

**Output esperado:**
```
PASS  (244 suites, 244 tests)
All tests passed
```

---

## 🏗️ PARTE 3: Funcionalidades en Vivo (60s)
**Tiempo: 2:00 - 3:00**

Mostrar en navegador:

### 1. HU05: Sugerencias de Amigos (15s)
```
GET /api/users/{userId}/suggestions
```
**Mostrar:** Lista de usuarios (2-hop graph traversal, mutual friends)

### 2. HU06: Media Upload (15s)
```
POST /api/media/upload (multipart file)
```
**Mostrar:** File uploaded → S3/RustFS integration

### 3. HU07: Crear Post (15s)
```
POST /api/posts
Content: "Hola Wyrdly" + media
```
**Mostrar:** Post creado con autor, timestamp, media

### 4. HU08: Feed (15s)
```
GET /api/feed?page=1&pageSize=20
```
**Mostrar:** Posts de usuarios seguidos + posts propios, con reaction counts

### 5. HU09: Reacciones (15s)
```
POST /api/posts/{postId}/react
{"type": "LIKE"}
→ ADDED / UPDATED / REMOVED
```
**Mostrar:** Toggle reaction → estado sincronizado

---

## 🔧 PARTE 4: Arquitectura (30s)
**Tiempo: 3:00 - 3:30**

**Mostrar diagrama o terminal:**
```
Backend Stack:
├── Quarkus (REST, Security, DI)
├── Neo4j (Graph: Usuario, Post, Mensaje)
├── Redis (Rate limit, Idempotency)
└── JAX-RS Filters (Auth, Rate limit, Dedup)

Frontend Stack:
├── React 18 (Hooks, Context)
├── TypeScript (Type safety)
└── Axios (HTTP client)

Model Neo4j:
(:Usuario)-[:SIGUE]->(u2:Usuario)
(:Usuario)-[:PUBLICA]->(p:Post)
(:Usuario)-[:REACCIONA {tipo}]->(p:Post)
```

---

## 📊 PARTE 5: Resumen (30s)
**Tiempo: 3:30 - 4:00**

```
✅ 5 Historias de Usuario Completas (HU05-HU09)
✅ 378 Tests Pasando (134 backend + 244 frontend)
✅ Rate Limiting: 30 reacciones/min/usuario
✅ Idempotencia: 1s Redis window
✅ Concurrencia: 50+ usuarios simultáneos
✅ Integración Full-Stack: API ↔ DB ↔ UI
```

---

## 🎬 Comandos Quick Copy-Paste

```bash
# Backend tests
cd wyrdly-backend && ./mvnw test -DskipITs=false

# Frontend tests
cd wyrdly-frontend && npm test

# Check formatting
cd wyrdly-backend && ./mvnw spotless:check

# Run Docker containers (optional demo)
docker compose -f compose.yaml up
```

---

## 💡 Tips Grabación

1. **OBS Studio o similar**: Captura pantalla + micrófono
2. **Zoom terminal**: Ctrl++ (readability)
3. **Velocidad**: Play real-time tests (no acelerar, muestra confianza)
4. **Narración**: Calma, 1-2 frases por sección
5. **Backup**: Grabar GIF de cada endpoint respondiendo
