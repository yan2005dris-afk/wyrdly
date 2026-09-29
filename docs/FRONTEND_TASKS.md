# Wyrdly — Plan de Tareas de Componentización Frontend

Control de progreso detallado para la migración de los mockups en `docs/MOCKUPS/html/` a componentes modulares en React 19 + TypeScript + CSS Modules + Vitest en `wyrdly-frontend/`.

---

## 📐 Estándar por Componente (Separación Obligatoria)
Cada componente se implementa con su cuaterna de archivos:
1. `ComponentName.tsx` → Marcado HTML / JSX semántico y accesible.
2. `ComponentName.types.ts` → Tipado fuerte y estricto (0% `any`).
3. `ComponentName.module.css` → Estilos CSS modulares encapsulados.
4. `ComponentName.test.tsx` → Pruebas unitarias completas con Vitest y `@testing-library/react`.
5. `index.ts` → Re-exportación limpia.

---

## Leyenda de Estado
- `[ ]` **PENDIENTE**
- `[/]` **EN PROGRESO**
- `[x]` **COMPLETADO**

---

## 📌 Fase 1: Tipos Estrictos de Dominio (Sin `any`)
- [x] **T1.1**: Contrato de dominio base y perfiles (`wyrdly-frontend/src/types/domain.ts`)
- [x] **T1.2**: Contratos de feed, posts y reacciones (`wyrdly-frontend/src/types/feed.ts`)
- [x] **T1.3**: Contratos de chat en tiempo real y WebSocket (`wyrdly-frontend/src/types/chat.ts`)
- [x] **T1.4**: Contratos de notificaciones y Web Push VAPID (`wyrdly-frontend/src/types/notifications.ts`)
- [x] **T1.5**: Contratos de topología y salud de relays (`wyrdly-frontend/src/types/relay.ts`)
- [x] **T1.6**: Barrel export centralizado (`wyrdly-frontend/src/types/index.ts`)

---

## 📌 Fase 2: Componentes UI Base (Atómicos Reutilizables)
- [x] **T2.1: Avatar** (`wyrdly-frontend/src/components/ui/Avatar/`)
  - [x] `Avatar.types.ts`, `Avatar.module.css`, `Avatar.tsx`, `Avatar.test.tsx`, `index.ts`
- [x] **T2.2: Badge** (`wyrdly-frontend/src/components/ui/Badge/`)
  - [x] `Badge.types.ts`, `Badge.module.css`, `Badge.tsx`, `Badge.test.tsx`, `index.ts`
- [x] **T2.3: Tabs** (`wyrdly-frontend/src/components/ui/Tabs/`)
  - [x] `Tabs.types.ts`, `Tabs.module.css`, `Tabs.tsx`, `Tabs.test.tsx`, `index.ts`
- [x] **T2.4: Button & IconButton** (`wyrdly-frontend/src/components/ui/Button/`)
  - [x] `Button.types.ts`, `Button.module.css`, `Button.tsx`, `Button.test.tsx`, `index.ts`
- [x] **T2.5: Input & FormField** (`wyrdly-frontend/src/components/ui/Input/`)
  - [x] `Input.types.ts`, `Input.module.css`, `Input.tsx`, `Input.test.tsx`, `index.ts`
- [x] **T2.6**: Barrel export centralizado (`wyrdly-frontend/src/components/ui/index.ts`)

---

## 📌 Fase 3: Layouts Compartidos y Shell
- [x] **T3.1: AppNavbar** (`wyrdly-frontend/src/components/layout/AppNavbar/`)
  - [x] `AppNavbar.types.ts`, `AppNavbar.module.css`, `AppNavbar.tsx`, `AppNavbar.test.tsx`, `index.ts`
- [x] **T3.2: SidebarNav** (`wyrdly-frontend/src/components/layout/SidebarNav/`)
  - [x] `SidebarNav.types.ts`, `SidebarNav.module.css`, `SidebarNav.tsx`, `SidebarNav.test.tsx`, `index.ts`
- [x] **T3.3: ThreeColumnLayout** (`wyrdly-frontend/src/components/layout/ThreeColumnLayout/`)
  - [x] `ThreeColumnLayout.types.ts`, `ThreeColumnLayout.module.css`, `ThreeColumnLayout.tsx`, `ThreeColumnLayout.test.tsx`, `index.ts`
- [x] **T3.4**: Barrel export centralizado (`wyrdly-frontend/src/components/layout/index.ts`)

---

## 📌 Fase 4: Módulo de Dominio Social (Feed & Grafo)
- [x] **T4.1: UserSummaryCard** (`wyrdly-frontend/src/components/social/UserSummaryCard/`)
  - [x] `UserSummaryCard.types.ts`, `UserSummaryCard.module.css`, `UserSummaryCard.tsx`, `UserSummaryCard.test.tsx`, `index.ts`
- [x] **T4.2: CreatePostCard** (`wyrdly-frontend/src/components/social/CreatePostCard/`)
  - [x] `CreatePostCard.types.ts`, `CreatePostCard.module.css`, `CreatePostCard.tsx`, `CreatePostCard.test.tsx`, `index.ts`
- [x] **T4.3: PostCard** (`wyrdly-frontend/src/components/social/PostCard/`)
  - [x] `PostCard.types.ts`, `PostCard.module.css`, `PostCard.tsx`, `PostCard.test.tsx`, `index.ts`
- [x] **T4.4: GraphSuggestionsCard** (`wyrdly-frontend/src/components/social/GraphSuggestionsCard/`)
  - [x] `GraphSuggestionsCard.types.ts`, `GraphSuggestionsCard.module.css`, `GraphSuggestionsCard.tsx`, `GraphSuggestionsCard.test.tsx`, `index.ts`
- [x] **T4.5: RelayHealthWidget** (`wyrdly-frontend/src/components/social/RelayHealthWidget/`)
  - [x] `RelayHealthWidget.types.ts`, `RelayHealthWidget.module.css`, `RelayHealthWidget.tsx`, `RelayHealthWidget.test.tsx`, `index.ts`
- [x] **T4.6**: Barrel export centralizado (`wyrdly-frontend/src/components/social/index.ts`)

---

## 📌 Fase 5: Módulo de Perfil de Usuario
- [x] **T5.1: ProfileHeaderCard** (`wyrdly-frontend/src/components/profile/ProfileHeaderCard/`)
  - [x] `ProfileHeaderCard.types.ts`, `ProfileHeaderCard.module.css`, `ProfileHeaderCard.tsx`, `ProfileHeaderCard.test.tsx`, `index.ts`
- [x] **T5.2: PostGridItem** (`wyrdly-frontend/src/components/profile/PostGridItem/`)
  - [x] `PostGridItem.types.ts`, `PostGridItem.module.css`, `PostGridItem.tsx`, `PostGridItem.test.tsx`, `index.ts`
- [x] **T5.3**: Barrel export centralizado (`wyrdly-frontend/src/components/profile/index.ts`)

---

## 📌 Fase 6: Módulo de Chat WebSocket en Tiempo Real
- [x] **T6.1: ConversationItem & ConversationList** (`wyrdly-frontend/src/components/chat/ConversationList/`)
  - [x] `ConversationList.types.ts`, `ConversationList.module.css`, `ConversationList.tsx`, `ConversationList.test.tsx`, `index.ts`
- [x] **T6.2: MessageBubble** (`wyrdly-frontend/src/components/chat/MessageBubble/`)
  - [x] `MessageBubble.types.ts`, `MessageBubble.module.css`, `MessageBubble.tsx`, `MessageBubble.test.tsx`, `index.ts`
- [x] **T6.3: ChatHeader** (`wyrdly-frontend/src/components/chat/ChatHeader/`)
  - [x] `ChatHeader.types.ts`, `ChatHeader.module.css`, `ChatHeader.tsx`, `ChatHeader.test.tsx`, `index.ts`
- [x] **T6.4: ChatInputBar** (`wyrdly-frontend/src/components/chat/ChatInputBar/`)
  - [x] `ChatInputBar.types.ts`, `ChatInputBar.module.css`, `ChatInputBar.tsx`, `ChatInputBar.test.tsx`, `index.ts`
- [x] **T6.5: ChatWindow** (`wyrdly-frontend/src/components/chat/ChatWindow/`)
  - [x] `ChatWindow.types.ts`, `ChatWindow.module.css`, `ChatWindow.tsx`, `ChatWindow.test.tsx`, `index.ts`
- [x] **T6.6**: Barrel export centralizado (`wyrdly-frontend/src/components/chat/index.ts`)

---

## 📌 Fase 7: Módulo de Notificaciones y Web Push
- [x] **T7.1: PushPermissionBanner** (`wyrdly-frontend/src/components/notifications/PushPermissionBanner/`)
  - [x] `PushPermissionBanner.types.ts`, `PushPermissionBanner.module.css`, `PushPermissionBanner.tsx`, `PushPermissionBanner.test.tsx`, `index.ts`
- [x] **T7.2: NotificationItem** (`wyrdly-frontend/src/components/notifications/NotificationItem/`)
  - [x] `NotificationItem.types.ts`, `NotificationItem.module.css`, `NotificationItem.tsx`, `NotificationItem.test.tsx`, `index.ts`
- [x] **T7.3: NotificationPopover** (`wyrdly-frontend/src/components/notifications/NotificationPopover/`)
  - [x] `NotificationPopover.types.ts`, `NotificationPopover.module.css`, `NotificationPopover.tsx`, `NotificationPopover.test.tsx`, `index.ts`
- [x] **T7.4**: Barrel export centralizado (`wyrdly-frontend/src/components/notifications/index.ts`)

---

## 📌 Fase 8: Refactorización y Ensamblado de Páginas
- [x] **T8.1: Desacoplar AuthPage** (`wyrdly-frontend/src/pages/AuthPage.tsx` -> `AuthGraphHero` + `AuthFormCard`)
  - [x] `AuthGraphHero.types.ts`, `AuthGraphHero.module.css`, `AuthGraphHero.tsx`, `AuthGraphHero.test.tsx`, `index.ts`
  - [x] `AuthFormCard.types.ts`, `AuthFormCard.module.css`, `AuthFormCard.tsx`, `AuthFormCard.test.tsx`, `index.ts`
  - [x] Refactorización completa de `AuthPage.tsx`
  - [x] Validación de `AuthPage.test.tsx`
- [x] **T8.2: Implementar FeedPage completa** (`wyrdly-frontend/src/pages/FeedPage.tsx`)
  - [x] `FeedPage.tsx` ensamblando `ThreeColumnLayout`, `AppNavbar`, `UserSummaryCard`, `SidebarNav`, `CreatePostCard`, `PostCard`, `GraphSuggestionsCard`, `RelayHealthWidget`
  - [x] `FeedPage.test.tsx`
- [x] **T8.3: Implementar ProfilePage** (`wyrdly-frontend/src/pages/ProfilePage.tsx`)
  - [x] `ProfilePage.tsx` ensamblando `ProfileHeaderCard` y `PostGridItem`
  - [x] `ProfilePage.test.tsx`
- [x] **T8.4: Implementar ChatPage** (`wyrdly-frontend/src/pages/ChatPage.tsx`)
  - [x] `ChatPage.tsx` ensamblando `ConversationList` y `ChatWindow`
  - [x] `ChatPage.test.tsx`
- [x] **T8.5: Actualizar rutas en App.tsx** (`wyrdly-frontend/src/App.tsx`)
  - [x] Rutas conectadas: `/login`, `/register`, `/feed`, `/profile`, `/profile/:username`, `/chat`, `/explore`, `/notifications`

---

## 📌 Fase 9: Calidad, Tipos y Pruebas Globales
- [x] **T9.1**: Chequeo estricto del compilador TypeScript (`pnpm run build` / `tsc -b`) - 0 errores, 0 `any`
- [x] **T9.2**: Ejecución completa de la suite de pruebas unitarias (`pnpm test` / `vitest run`) - 30/30 suites pasadas, 85/85 tests pasados
- [x] **T9.3**: Linter y formateo (`pnpm lint`) - 0 errores, 0 warnings
- [x] **T9.4**: Build de producción validado (`pnpm run build`) - Generado en 2.49s sin advertencias
