# ADR-002: Estructura de Paquetes en Backend (Domain, Application, Infrastructure, Interfaces)

## Estado
Aceptado

## Contexto
Inicialmente, los módulos del backend en Quarkus adoptaban una convención de Arquitectura Hexagonal con subpaquetes `in` y `out` (puertos primarios/secundarios y adaptadores de entrada/salida). Si bien este enfoque desacopla puertos y adaptadores, la nomenclatura `in`/`out` resultaba verbosa y menos intuitiva para la organización por capas del dominio y la navegación del código.

## Decisión
Adoptar una arquitectura por capas basada en principios de **Domain-Driven Design (DDD) / Onion Architecture**, estructurando cada módulo funcional del backend (`com.wyrdly.<modulo>`) en 4 capas bien definidas:

1. **`domain` (Núcleo de Negocio):**
   - **`model/`:** Entidades y Value Objects puros del dominio (ej. `User`, `AuthTokens`).
   - **`repository/`:** Interfaces de persistencia del dominio (ej. `UserRepository`).
   - **`exception/`:** Excepciones de negocio y reglas de dominio.

2. **`application` (Casos de Uso y Orquestación):**
   - **`usecase/`:** Interfaces de casos de uso de la aplicación (ej. `RegisterUserUseCase`, `AuthenticateUserUseCase`, `RefreshTokenUseCase`).
   - **`port/`:** Interfaces de servicios externos requeridos por la aplicación (ej. `PasswordHasherPort`, `TokenProviderPort`).
   - **`service/`:** Implementación de la lógica de aplicación y casos de uso (ej. `AuthService`).
   - **`dto/`:** Data Transfer Objects de la capa de aplicación (ej. `LoginRequest`, `AuthResponse`).

3. **`infrastructure` (Adaptadores Técnicos y Servicios Externos):**
   - **`persistence/`:** Implementaciones de repositorios con bases de datos (ej. `Neo4jUserRepositoryAdapter`).
   - **`crypto/`:** Implementaciones de hashing y criptografía (ej. `BCryptPasswordHasherAdapter`).
   - **`security/`:** Proveedores y emisores de tokens JWT (ej. `SmallRyeJwtTokenAdapter`).

4. **`interfaces` (Capa de Entrega / Presentación):**
   - **`rest/`:** Recursos JAX-RS / Quarkus REST endpoints y manejadores de excepciones (`AuthResource`, `AuthExceptionMappers`).

## Consecuencias
- **Positivas:**
  - Nomenclatura estándar, semántica y clara de las capas sin ambigüedad sobre `in`/`out`.
  - Mantiene el principio de inversión de dependencias: el dominio y la aplicación no dependen de la infraestructura ni de los frameworks de entrega.
  - Facilidad de testeo con mocks orientados a interfaces de repositorio y casos de uso.
- **Negativas / Mitigaciones:**
  - Requiere mantener la consistencia de estos paquetes en futuros módulos (`post`, `chat`, `notification`).
