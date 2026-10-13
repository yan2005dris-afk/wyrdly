## Estado de Réplica:  ACEPTADO CON OBSERVACIONES

Agradecemos la revisión del equipo auditor. Tras analizar el componente `PostCard`, la capa de API de publicaciones y los requisitos establecidos en el enunciado de la actividad, determinamos lo siguiente:

## 1. Reconocimiento del problema

Se acepta el defecto de interfaz reportado:

- En `wyrdly-frontend/src/features/social/components/PostCard/PostCard.tsx` (líneas 158–164), el botón de opciones (`MoreHorizontal`, `aria-label="Post options"`) se renderiza sin manejador de eventos ni menú asociado. Presenta estilos de interacción (hover de `kebabButton`) pero no ejecuta ninguna acción, constituyendo un *dead click*.
- El botón carece de respaldo en el backend: `PostResource.java` únicamente expone `POST /api/posts` (crear), `POST /api/posts/{postId}/react` (reaccionar) y `GET /api/posts/{postId}` (consultar).
- Reconocemos además que el enunciado de la actividad (sección 11, *Restricciones*) establece que *"una funcionalidad que aparezca en la interfaz pero que no esté integrada correctamente con la arquitectura correspondiente se considerará incompleta"*. Un botón visible sin integración incumple este criterio, por lo que el hallazgo es procedente.

## 2. Observaciones técnicas y contextualización

**Alcance de las opciones esperadas vs. requisitos funcionales:**
El resultado esperado del hallazgo sugiere un menú con opciones para *Eliminar, Editar o Reportar*. Sin embargo, ninguna de estas operaciones forma parte de las funcionalidades mínimas del enunciado:

- La sección 3.3 (*Publicaciones*) exige únicamente que los usuarios **puedan crear publicaciones** con autor, texto, fecha/hora y recurso multimedia opcional, junto con su relación `(:Usuario)-[:PUBLICA]->(:Post)`.
- Las secciones 3.5 y 3.6 cubren el feed y las reacciones.
- La sección 6 (*API REST*) lista como referencia para publicaciones solo `POST /posts` y `GET /feed`.

Todos estos requisitos están implementados e integrados. Por tanto, el defecto corresponde a un elemento visual residual y no a la ausencia de una funcionalidad requerida.

**Contexto de desarrollo (MVP en 1 semana):**
El sprint priorizó los flujos exigidos por el enunciado: autenticación, grafo social, publicaciones con almacenamiento S3, feed basado en relaciones, reacciones, chat por WebSocket y notificaciones Web Push. El botón se incluyó en el diseño visual anticipando funcionalidades futuras, sin ocultarse ni marcarse como no disponible.

**Severidad:**
Concordamos con la clasificación **Baja**. El defecto no afecta la integridad de datos, la seguridad ni ninguno de los flujos obligatorios; su impacto se limita a la consistencia de la interfaz.

## 3. Acciones correctivas planificadas

**Corrección inmediata:**
Eliminar el botón de opciones de `PostCard` mientras no exista funcionalidad integrada, cumpliendo tanto el criterio del hallazgo como la sección 11 del enunciado.

**Funcionalidad planificada (evolución del producto):**
Aunque no es un requisito del enunciado, el equipo planea incorporar **edición y eliminación de publicaciones propias**:

- **Backend:** `PATCH /api/posts/{postId}` y `DELETE /api/posts/{postId}`, validando la autoría mediante la relación `(:Usuario)-[:PUBLICA]->(:Post)` (respuesta `403 Forbidden` si el solicitante no es el autor). La eliminación limpiará también las relaciones asociadas (`REACCIONA`, comentarios, reposts) y el recurso multimedia en el almacenamiento S3, evitando objetos huérfanos.
- **Frontend:** reintroducir el botón con un menú contextual que muestre *Editar/Eliminar* solo en publicaciones del usuario autenticado, accesible por teclado y con atributos `aria-haspopup`/`aria-expanded`.

**Reportes de contenido:**
El reporte de un post queda fuera del alcance actual. Se evaluará en una iteración posterior y la opción *Reportar* no se expondrá en la interfaz hasta contar con su flujo completo en backend.
