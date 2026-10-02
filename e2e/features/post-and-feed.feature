# language: es
Característica: Creación de Publicaciones y Visualización de Feed (HU07 y HU08)
  Como usuario autenticado en Wyrdly
  Quiero publicar actualizaciones de texto
  Para compartir contenido con mis seguidores y visualizarlo en el feed social

  Antecedentes:
    Dado que existe un usuario autenticado en la sesión
    Y se encuentra en la página de feed principal

  Escenario: Publicación exitosa de un post de texto en el feed
    Cuando escribe el contenido "Prueba E2E automatizada con Cucumber y Playwright" en la caja de publicación
    Y presiona el botón de publicar
    Entonces la nueva publicación debe aparecer en el feed con el texto "Prueba E2E automatizada con Cucumber y Playwright"
