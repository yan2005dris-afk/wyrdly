# ADR-004: Adopción de Screaming Architecture y Feature-Sliced Design en Frontend

## Estado
Aceptado

## Contexto
El frontend (`wyrdly-frontend`) fue estructurado inicialmente agrupando el código por rol técnico a nivel de raíz en `src/`:
- `src/components/`: Componentes atómicos y de dominio mezclados.
- `src/hooks/`: Todos los custom hooks de negocio en un único directorio plano.
- `src/api/`: Todos los clientes de API REST en un único directorio plano.
- `src/types/`: Interfaces y contratos globales.

A medida que el producto creció incorporando múltiples dominios (autenticación, perfiles, feed social, sugerencias en grafo, chat en tiempo real y notificaciones), esta organización técnica presentó limitaciones de escalabilidad:
1. **Baja cohesión y dispersión:** Modificar o entender una funcionalidad (por ejemplo, el módulo de Chat) obliga a abrir archivos repartidos en 5 carpetas distintas (`src/components/chat/`, `src/hooks/`, `src/api/`, `src/types/`, `src/pages/`).
2. **Fugas de encapsulamiento:** Componentes internos de un dominio son fácilmente importados por otros sin una frontera clara de API pública, incrementando el acoplamiento cruzado.
3. **Dificultad de descarte o refactorización:** Eliminar o reemplazar un módulo deja con frecuencia código muerto o huérfano esparcido en las carpetas globales.

## Decisión
Adoptar **Screaming Architecture / Feature-Sliced Design** en `wyrdly-frontend/src`, organizando el código alrededor de los dominios de negocio en lugar de los tipos técnicos de archivo.

### 1. Estructura de Capas y Directorios

```text
src/
├── components/
│   └── ui/                     # Design System agnóstico (Button, Avatar, Input, Badge, Tabs)
│
├── features/                   # Dominios de negocio autocontenidos
│   ├── auth/                   # Autenticación, formularios de login/registro, guards
│   ├── profile/                # Perfil de usuario, edición interactiva, grid de publicaciones
│   ├── social/                 # Feed, creación de posts, sugerencias en grafo, follow/unfollow
│   ├── chat/                   # Salas, mensajería WebSocket, historial, presencia
│   └── notifications/          # Popover, listado y badge de notificaciones
│
├── pages/                      # Orquestadores de alto nivel por ruta (/feed, /profile, /chat)
├── context/                    # Estado global transversal (AuthContext)
├── utils/                      # Utilidades puras transversales (conversión WebP, avatar determinista)
├── api/                        # Cliente HTTP base y configuración de Axios / interceptores
└── types/                      # Tipos globales del sistema compartidos entre dominios
```

### 2. Anatomía de una Feature (`src/features/<modulo>/`)

Cada carpeta de feature debe actuar como un módulo encapsulado y autocontenido con su propia estructura interna:

```text
src/features/<modulo>/
├── components/                 # Componentes de presentación exclusivos del dominio
├── hooks/                      # Custom hooks y lógica de estado del dominio
├── api/                        # Clientes de API, endpoints REST o WebSockets del dominio
├── types/                      # Contratos, DTOs y tipos específicos del módulo
└── index.ts                    # ★ API PÚBLICA (Barrel Export del módulo)
```

### 3. Reglas de Encapsulamiento y Dependencias

1. **Frontera de API Pública (`index.ts`):**
   - Una feature solo expone al resto del sistema aquello que exporta explícitamente en su `index.ts`.
   - Quedan estrictamente prohibidos los imports profundos hacia el interior de otra feature:
     - `CORRECTO:` `import { EditProfileModal } from "@/features/profile";`
     - `PROHIBIDO:` `import { EditProfileModal } from "@/features/profile/components/EditProfileModal/EditProfileModal";`
2. **Aislamiento entre Features:**
   - Una feature no debe importar submódulos privados de otra feature. Si dos features requieren compartir componentes visuales, estos deben residir en `components/ui/`; si comparten datos globales, deben orquestarse en la capa de `pages/` o mediante un `context/` transversal.
3. **Colocation estricto:**
   - Todo componente dentro de `components/` mantiene sus archivos adyacentes: lógica (`.tsx`), módulos CSS (`.module.css`), contratos (`.types.ts`) y pruebas unitarias (`.test.tsx`).

### 4. Estrategia de Migración Gradual

Para no desestabilizar la base de código ni romper las pruebas unitarias existentes (216 tests pasando), la migración se ejecutará en pasos incrementales por feature en una rama dedicada:
1. `features/auth/`
2. `features/profile/`
3. `features/social/`
4. `features/chat/`
5. `features/notifications/`
6. Limpieza y consolidación de exportaciones globales.

## Consecuencias

- **Positivas:**
  - **Screaming Architecture:** La estructura del código revela de inmediato el negocio de una red social distribuida y sus capacidades clave.
  - **Alta cohesión:** Todo lo relacionado a un dominio de negocio evoluciona y se mantiene en un solo lugar.
  - **Mantenibilidad y desacoplamiento:** Las APIs públicas (`index.ts`) protegen la implementación interna de cambios que afecten al resto de la app.
  - **Eliminación atómica:** Remover o reemplazar una feature completa requiere borrar únicamente su directorio.
- **Negativas / Mitigaciones:**
  - Requiere disciplina de equipo para evitar imports internos profundos (mitigable mediante reglas de ESLint `no-restricted-imports`).
  - Mayor cantidad de archivos `index.ts` para definir las fronteras de exportación.
