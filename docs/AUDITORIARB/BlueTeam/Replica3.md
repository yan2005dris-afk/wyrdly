# Estado de Réplica: ACEPTADO CON OBSERVACIONES

> Agradecemos la revisión técnica del Red Team. Tras verificar la evidencia en el commit auditado, determinamos lo siguiente:

## 1. Reconocimiento del problema
Se **acepta** la inconsistencia observada en la interfaz:
- El input file en `CreatePostCard.tsx` declara `accept="image/*,video/*"`, permitiendo seleccionar archivos de video que posteriormente son rechazados por `useMediaUpload.ts`.
- `CreatePostCard.tsx` captura el fallo retornando el mensaje genérico `"Upload failed. Please try again."` en lugar de propagar el mensaje descriptivo definido en el hook (`UNSUPPORTED_TYPE_MESSAGE`).
- El componente `PostCard.tsx` implementa renderizado para `<AuthImage>`, sin soporte actual para tags de video en el feed.

## 2. Observaciones técnicas y contextualización

1. **Cumplimiento de la declarativa de cátedra (Requisitos mínimos):**
    - El documento base (*Actividad práctica – Red Social Distribuida*, Secciones 3.3 y 3.4) establece como requisito obligatorio: *"Recurso multimedia opcional"* y *"Las imágenes u otros archivos asociados a las publicaciones deberán almacenarse mediante un servicio compatible con S3"*.
    - El objetivo arquitectónico central (desacoplamiento total de binarios hacia RustFS/S3 sin almacenar BLOBs en el grafo Neo4j) se encuentra implementado y operativo al 100% mediante el pipeline de imágenes.

2. **Decisión de arquitectura documentada:**
    - En el commit `aaab5d1`, el equipo priorizó un pipeline de optimización en cliente que convierte imágenes rasterizadas (PNG, JPEG, GIF, BMP) a `image/webp` antes del envío, protegiendo el almacenamiento y la cuota de red de RustFS. Como consecuencia técnica, el backend se acotó temporalmente a `media.allowed-mime-types=image/webp`, aplazando el pipeline dedicado de transcodificación de video (MP4) a un release posterior.

3. **Reclasificación de Severidad:**
    - La severidad debe catalogarse como **Baja / UX-Defect** en lugar de **Media (Incumplimiento de Requisitos)**, ya que la funcionalidad core de posts multimedia con RustFS opera correctamente y no existe incumplimiento de los requisitos mínimos obligatorios de la entrega.

## 3. Acciones correctivas planificadas
- Alinear el atributo `accept` en el input de `CreatePostCard` a solo tipos soportados (`accept="image/*"`).
- Propagar el mensaje de error específico provisto por `useMediaUpload` en la UI para evitar feedback ambiguo al usuario.
