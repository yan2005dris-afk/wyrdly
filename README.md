# Wyrdly (Red Social Distribuida)

[![Quarkus](https://img.shields.io/badge/Backend-Quarkus%203.x-red?logo=quarkus)](https://quarkus.io/)
[![Neo4j](https://img.shields.io/badge/Database-Neo4j%205.x-blue?logo=neo4j)](https://neo4j.com/)
[![React](https://img.shields.io/badge/Frontend-React%2018%20%2B%20Vite-61DAFB?logo=react)](https://react.dev/)
[![RustFS](https://img.shields.io/badge/Storage-RustFS%20(S3)-DEA584?logo=rust)](https://github.com/rustfs/rustfs)
[![Docker](https://img.shields.io/badge/Infra-Docker%20Compose-2496ED?logo=docker)](https://www.docker.com/)

**Wyrdly** es una plataforma de red social distribuida de alto rendimiento construida con arquitectura orientada a grafos (**Neo4j**), servicios reactivos en **Quarkus (Java 21)**, almacenamiento de objetos compatible con S3 en **RustFS**, frontend moderno en **React (Vite + Tailwind CSS)**, mensajería instantánea bidireccional mediante **WebSockets** y notificaciones **Web Push (VAPID)**.

El nombre **Wyrd** viene del inglés antiguo y significa "el tejido del destino" — la interconexión inevitable de todas las cosas. En una red distribuida, cada usuario, cada nodo, cada mensaje es un hilo en ese tejido.

---

## 👥 Equipo de Desarrollo (Wyrdly)

| Integrante | Rol Principal | Responsabilidad |
| :--- | :--- | :--- |
| **Yandris** | Arquitectura & Tech Lead | Diseño de arquitectura, contratos de API y orquestación general |
| **Gino** | Backend Engineer 1 | Autenticación JWT, integración S3 (RustFS) y Web Push (VAPID) |
| **Andy** | Backend Engineer 2 | Modelado y algoritmos de grafos Cypher (Neo4j) y WebSockets (Chat) |
| **Allison** | Frontend Engineer | SPA React, maquetación Tailwind CSS, consumo de API y Service Workers |

---

## 🏗️ Arquitectura del Sistema

```
                      ┌───────────────────────────────────────┐
                      │          React SPA (Vite + TS)        │
                      │  (Feed, Perfiles, Chat WS, Web Push)  │
                      └──────────────────┬────────────────────┘
                                         │ HTTP REST / WebSocket
                                         ▼
                      ┌───────────────────────────────────────┐
                      │         Backend Quarkus 3.x           │
                      │   (Java 21, RESTEasy Reactive, JWT)   │
                      └───────┬──────────────────────┬────────┘
                              │                      │
                  Cypher Bolt │                      │ S3 SDK
                              ▼                      ▼
               ┌───────────────────────┐    ┌─────────────────┐
               │    Neo4j Graph DB     │    │  RustFS Cluster │
               │ (Nodos, Aristas, Feed)│    │ (Multimedia S3) │
               └───────────────────────┘    └─────────────────┘
```

### Componentes Principales
- **Backend (Quarkus 3.x):** API REST reactiva, seguridad JWT, endpoints WebSocket `/ws/chat` y cliente Web Push VAPID.
- **Base de Datos (Neo4j 5.x):** Grafo con nodos `:Usuario`, `:Post`, `:Mensaje` y relaciones dirigidas `[:SIGUE]`, `[:PUBLICA]`, `[:REACCIONA]`, `[:ENVIA]`, `[:DIRIGIDO_A]`.
- **Almacenamiento de Objetos (RustFS):** Servidor de almacenamiento en Rust compatible con AWS S3 API para subida y entrega de avatares, fotos y videos.
- **Frontend (React + Vite):** Interfaz desacoplada con gestión de estado, suscripción a Service Worker para notificaciones push y cliente de chat reactivo.

> 📊 **Diagramas Interactivos (Archify):** Visualización interactiva del flujo distribuido del sistema con trazabilidad de eventos, temas claro/oscuro y visualización por capas:
> - **[Arquitectura de Plataforma Wyrdly](./docs/architecture/wyrdly-platform.html)**
> - **[Flujo de Comunicación entre Servicios](./docs/architecture/service-communication.html)**
> - **[Build Archify (.archify)](./.archify/architecture-wyrdly-distributed-social-network-20261007-212630/wyrdly-distributed-social-network.html)**

---

## 📚 Documentación del Proyecto

Toda la especificación técnica y de negocio se encuentra en el directorio [`docs/`](./docs):

- **[Diagramas Interactivos de Arquitectura (Archify)](./docs/architecture/wyrdly-platform.html):** Gráficos interactivos de flujo y arquitectura con animación de trazas y visualización dinámica.
- **[Guía de Despliegue Local](./docs/deployment/LOCAL_DEPLOYMENT.md):** Manual paso a paso para levantar el entorno distribuido local con Docker Compose o en modo desarrollo híbrido.
- **[PRD (Product Requirements Document)](./docs/prd/PRD.md) | [PRD.docx](./docs/prd/PRD.docx):** Matriz de requerimientos, alcance y desglose de Historias de Usuario (HU01 - HU11) con criterios de aceptación Gherkin.
- **[TRD (Technical Requirements Document)](./docs/trd/TRD.md) | [TRD.docx](./docs/trd/TRD.docx):** Decisiones de arquitectura, topología de servicios Docker y justificaciones técnicas.
- **[Contratos de API](./docs/api-contracts/API_CONTRACT.md) | [API_CONTRACT.docx](./docs/api-contracts/API_CONTRACT.docx):** Especificación completa de endpoints REST, payloads JSON con envoltorio `data`/`meta`, frames WebSocket y eventos Web Push.
- **[Esquema de Grafo Neo4j](./docs/database/NEO4J_SCHEMA.md) | [NEO4J_SCHEMA.docx](./docs/database/NEO4J_SCHEMA.docx):** Modelo formal de nodos, relaciones, índices, restricciones y consultas Cypher optimizadas.
- **[Mockups Interactivos (HTML)](./docs/MOCKUPS/html/index.html):** Prototipos visuales de autenticación, feed, perfil, chat y notificaciones.

---

## ⚡ Despliegue Local Rápido (Docker Compose)

Para levantar todos los servicios en local con una sola orden:

```bash
# 1. Configurar variables de entorno y claves JWT
cp .env.example .env
./scripts/generate-jwt-keys.sh ./jwt

# 2. Iniciar contenedores en segundo plano
docker compose up -d --build
```

> Consulta la **[Guía de Despliegue Local](./docs/deployment/LOCAL_DEPLOYMENT.md)** para detalles completos, credenciales pre-cargadas (seed data), modo desarrollo híbrido (Quarkus Dev + Vite HMR) y resolución de problemas.

---

## 📋 Historias de Usuario

| ID | Historia de Usuario | Estado |
| :--- | :--- | :---: |
| **HU01** | Registro de Usuarios con Validaciones | 📐 Documentado |
| **HU02** | Autenticación e Inicio de Sesión (JWT) | 📐 Documentado |
| **HU03** | Consulta y Actualización de Perfiles | 📐 Documentado |
| **HU04** | Sistema de Seguimiento (Follow / Unfollow) | 📐 Documentado |
| **HU05** | Sugerencias Inteligentes de Amistad (Grafo) | 📐 Documentado |
| **HU06** | Subida de Archivos Multimedia a RustFS (S3) | 📐 Documentado |
| **HU07** | Creación y Publicación de Posts | 📐 Documentado |
| **HU08** | Feed Social Personalizado por Recorrido de Grafo | 📐 Documentado |
| **HU09** | Reacciones a Publicaciones (Like, Love, Celebrate) | 📐 Documentado |
| **HU10** | Chat Bidireccional en Tiempo Real vía WebSockets | 📐 Documentado |
| **HU11** | Notificaciones Web Push Desacopladas (VAPID) | 📐 Documentado |

---

## 🚀 Flujo de Ramas (Git Flow)

- `main`: Rama de documentación y versiones estables de entrega.
- `develop`: Rama principal de integración y desarrollo activo (rama por defecto).
- `feature/<hu-nombre>`: Ramas de trabajo individuales para cada historia de usuario.
