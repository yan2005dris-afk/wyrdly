**ID:** UX-004
**Categoría:** Experiencia de Usuario (UX)
**Severidad:** Baja

### Descripción
En el componente de la tarjeta de publicación (`PostCard`), existe un botón de menú de opciones (tres puntos `...`) ubicado en la esquina superior derecha de cada post. Sin embargo, este elemento no tiene ningún evento asociado.

### Pasos exactos para reproducir
1. Ir al feed de publicaciones.
2. Hacer clic en el icono de los tres puntos de cualquier publicación propia o ajena.

### Resultado esperado
Debería desplegarse un menú contextual (ej. opciones para Eliminar, Editar o Reportar el post). Si la funcionalidad no está implementada, el botón no debería existir en la interfaz final para evitar confusión.

### Resultado obtenido
El botón es interactivo al pasar el cursor, pero no produce ninguna acción ni despliega ningún menú, resultando en un elemento fantasma ("Dead Click").
