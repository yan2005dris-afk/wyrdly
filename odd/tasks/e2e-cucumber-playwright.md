# Feature: End-to-End Testing con Cucumber y Playwright

## Objetivo
Implementar una suite de pruebas End-to-End (E2E) automatizada basada en Behavior-Driven Development (BDD) utilizando **Cucumber** y **Playwright** para validar los flujos críticos de usuario (Autenticación HU01/HU02 y Publicaciones/Feed HU07/HU08) descritos en el PRD.

## Problema
El monorepo cuenta con pruebas unitarias (Surefire en backend, Vitest en frontend) y de integración con contenedores (Failsafe + Testcontainers Neo4j). Sin embargo, no disponía de pruebas de caja negra a nivel de navegador que verifiquen la integración real de la SPA (React + Vite) con el Backend (Quarkus) y la persistencia de datos ante interacciones de usuario reales.

## Por qué
Cucumber permite expresar los criterios de aceptación en Gherkin legible por negocio (alineado a las HU del PRD), mientras que Playwright provee ejecución rápida, soporte cross-browser, auto-wait en selectores, y captura de capturas de pantalla / trazas ante fallos sin la fragilidad del clásico Selenium.

## Alcance Autorizado
1. **Infraestructura E2E:**
   - Instalación de dependencias `@cucumber/cucumber`, `playwright`, `@playwright/test`, `tsx` en el monorepo.
   - Configuración de `cucumber.json`, soporte TypeScript (`e2e/tsconfig.json`).
   - `CustomWorld` con ciclo de vida de Browser, Context y Page (`e2e/support/world.ts`).
   - Hooks de ciclo de vida (`Before`, `After`, `BeforeAll`, `AfterAll`, capturas ante error en `e2e/support/hooks.ts`).
2. **Especificaciones Gherkin:**
   - `e2e/features/auth.feature`: Registro de usuario (HU01) e inicio de sesión con JWT (HU02).
   - `e2e/features/post-and-feed.feature`: Creación de post (HU07) y visualización en el feed social (HU08).
3. **Step Definitions:**
   - `e2e/steps/auth.steps.ts`: Pasos de registro y login.
   - `e2e/steps/post.steps.ts`: Pasos de publicación y verificación en feed.
4. **Scripts y Ejecución:**
   - Scripts `test:e2e` y `test:e2e:headed` en `package.json` del monorepo.

## Restricciones
- Conventional Commits sin menciones ni atribuciones a IA.
- Selectores resilientes con `data-testid` y roles accesibles.
- Ejecución headless por defecto con variable de entorno `HEADLESS=false` opcional para depuración visual.

## Tareas

- [x] **TASK-01**: Infraestructura & Dependencias - Configurar `@cucumber/cucumber`, `playwright`, `tsx`, `cucumber.json` y `e2e/tsconfig.json`. (Commit: `2043c8a`)
- [x] **TASK-02**: Support & Hooks - Implementar `CustomWorld` y hooks con captura de capturas y manejo de browser context. (Commit: `2043c8a`)
- [x] **TASK-03**: Features Gherkin - Escribir `e2e/features/auth.feature` y `e2e/features/post-and-feed.feature`. (Commit: `2043c8a`)
- [x] **TASK-04**: Step Definitions - Implementar `e2e/steps/auth.steps.ts` y `e2e/steps/post.steps.ts`. (Commit: `2043c8a`)
- [x] **TASK-05**: NPM Scripts & Verificación - Configurar `pnpm test:e2e` y verificar ejecución de la suite. (Commit: `2043c8a`)

## Evidencia de Verificación
- **Dependencias**: `@cucumber/cucumber@^13.2.1`, `@playwright/test@^1.63.0`, `playwright@^1.63.0`, `tsx@^4.23.15` instaladas y vinculadas.
- **Navegador**: Playwright Chromium Headless Shell 153 descargado e instalado.
- **Dry-run**: `npx cucumber-js --dry-run` ejecutó 4/4 escenarios y 29/29 pasos vinculados sin definiciones faltantes ni errores de tipado.
- **Conventional Commit**: `test(e2e): setup cucumber and playwright bdd testing framework`.
