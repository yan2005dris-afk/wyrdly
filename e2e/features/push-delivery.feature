# ==============================================================================
# STRATEGY & DEPENDENCY DOCUMENTATION (TASK-11 / FE-PUSH-3.4 / Issue #71)
# ==============================================================================
# 1. Push Service Mock Strategy for CI/Headless:
#    Browser push gateways (e.g., FCM for Chrome, Mozilla Push Service for Firefox)
#    require vendor infrastructure and cannot deliver push notifications out-of-the-box
#    in headless CI environments. This suite uses Playwright notification permissions
#    (context.grantPermissions(['notifications'])) coupled with the active W3C
#    Service Worker registration (navigator.serviceWorker.ready) to validate the
#    Service Worker notification contract, presentation, and click-through navigation.
#
# 2. DEPENDENCY ON ISSUE #32 ([HU07->HU11] Disparar Web Push a seguidores):
#    Este escenario valida la recepcion de notificaciones Web Push en el cliente
#    de "bob" cuando "alice" crea una publicacion. El trigger automatico en el backend
#    depende directamente de la implementacion de:
#    - Issue #32: [HU07->HU11] Disparar notificaciones Web Push a seguidores cuando un autor publica un post
#
#    IMPORTANTE / REGLA DE INTEGRACION:
#    Este test implementa una simulacion/mock del envio push sobre el Service Worker
#    activo en el navegador del suscriptor. Tan pronto como el Issue #32 este completado,
#    aprobado y mergeado en develop con el hook asincrono en PostService, se debe
#    retirar el mock intermedio para que el escenario opere contra el despacho
#    100% integrado end-to-end.
# ==============================================================================

Feature: Web Push delivery

  Background:
    Given dos usuarios registrados: "alice" y "bob"
    And "alice" sigue a "bob"
    And "bob" tiene permiso de notificaciones y un SW suscrito

  Scenario: bob recibe push cuando alice publica
    When "alice" crea un post con contenido "Hola mundo push"
    Then "bob" recibe una push con título que contiene "alice"
    And la body contiene snippet "Hola mundo push"
    When "bob" hace click en la notificación
    Then la app navega a "/posts/{postId}"
