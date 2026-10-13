**ID:** REQ-002
**Categoría:** Requisitos
**Severidad:** Media

### Descripción
La declaración de la HU06 especifica la integración con RustFS para la subida de "fotos y videos". Mientras que las imágenes se suben correctamente, intentar cargar cualquier formato de video estándar (como `.mp4` o `.mkv`) resulta en un fallo inmediato en el frontend.

### Pasos exactos para reproducir
1. Ir a crear un nuevo post.
2. Hacer clic en "Media".
3. Seleccionar un archivo de video válido (ej. `video.mp4` o `video.mkv`).

### Resultado esperado
El video debe subirse a RustFS, adjuntarse al borrador del post y reproducirse adecuadamente en el Feed.

### Resultado obtenido
El sistema rechaza los videos devolviendo un error genérico en la interfaz: "Upload failed. Please try again." El requisito de soportar videos está incumplido.
