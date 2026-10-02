# language: es
Característica: Autenticación de Usuarios (HU01 y HU02)
  Como visitante de la red social distribuida Wyrdly
  Quiero registrar una cuenta e iniciar sesión
  Para interactuar con la comunidad y publicar en el grafo

  Antecedentes:
    Dado que el usuario navega a la página de autenticación

  Escenario: Registro exitoso de un nuevo usuario (HU01)
    Dado que selecciona la pestaña de registro
    Cuando completa el formulario con un nombre completo, un usuario único, un correo y una contraseña válida
    Y presiona el botón de registro
    Entonces el sistema debe redirigirlo al feed principal
    Y el usuario debe estar autenticado en la sesión

  Escenario: Inicio de sesión exitoso con credenciales válidas (HU02)
    Dado que selecciona la pestaña de inicio de sesión
    Cuando ingresa sus credenciales válidas
    Y presiona el botón de inicio de sesión
    Entonces el sistema debe redirigirlo al feed principal

  Escenario: Intento de inicio de sesión con contraseña inválida
    Dado que selecciona la pestaña de inicio de sesión
    Cuando ingresa un usuario existente con una contraseña incorrecta
    Y presiona el botón de inicio de sesión
    Entonces se debe mostrar un mensaje de error en pantalla
