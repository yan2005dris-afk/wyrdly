# language: es
Característica: Notificaciones in-app (popover con datos reales)
  Como usuario autenticado en Wyrdly
  Quiero ver en la campanita las notificaciones generadas por follows y reacciones
  Para enterarme de lo que pasa en mi red sin recargar la página

  Antecedentes:
    Dado que existe un usuario autenticado en la sesión
    Y un usuario secundario "usr_actor_e2e" registrado en el sistema
    Y se encuentra en la página de feed principal

  Escenario: Un nuevo seguidor aparece en la campanita
    Cuando el usuario "usr_actor_e2e" sigue al usuario actual
    Y la campanita se vuelve a renderizar tras la siguiente consulta
    Entonces la campanita debe mostrar al menos 1 notificación no leída
    Y abre el popover de notificaciones
    Y la notificación "GRAPH_FOLLOW" del actor debe estar visible

  Escenario: Marcar todas como leídas resetea el contador
    Cuando el usuario "usr_actor_e2e" sigue al usuario actual
    Y la campanita se vuelve a renderizar tras la siguiente consulta
    Y abre el popover de notificaciones
    Y hace click en "Marcar todas como leídas"
    Entonces el contador no leídas de la campanita debe ser 0
