# Cleanup pendientes repo — rama chore/repo-cleanup-pendientes

Base: develop @ 72dfca4. Objetivo: dejar el repo sin duplicados, sin vacíos y sin docs académicos sueltos.

## Alcance autorizado
- `docs/Diagramas/DiagramaDeFlujo (1).png` (duplicado, verificar hash antes de borrar)
- `src/` raíz y `odd/hu11-web-push-notifications/` (verificar vacíos antes de borrar)
- `odd/tasks/` 27 archivos (decidir: archivar / borrar / mover, NO borrar a ciegas)
- `docs/DOCUMENTO_PRESENTACION.md`, `docs/FRONTEND_TASKS.md` (decidir: archivar o borrar)

## Checklist
- [ ] T1 Verificar vacíos: `src/` y `odd/hu11-web-push-notifications/` (ls -R + find, ruta declarada: inline)
- [x] T2 Duplicado: HECHO (2026-10-11). `DiagramaDeFlujo.png` (175.7K, oscuro) completo: flechas Muestra Notificación + Push Event visibles y marco exterior completo. `DiagramaDeFlujo (1).png` (174.4K, claro) mismo contenido pero con flechas cortadas/arriba truncadas. Ninguno referenciado en docs (grep solo cita este tracking). Decisión: conservado el oscuro original, borrado el `(1)` claro con `git rm` (staged).
- [ ] T3 `odd/tasks/` 27 archivos: definir destino con el usuario antes de mover/borrar (ruta: por definir)
- [ ] T4 Docs académicos: definir destino con el usuario antes de mover/borrar (ruta: por definir)

## Criterios de aceptación
- `git status` limpio salvo los deletes/moves intencionales en esta rama.
- Ningún delete sin evidencia previa (hash o ls) registrada en este doc.
- T3 y T4 no se ejecutan sin decisión explícita del usuario.

## Progreso
- Rama creada desde develop. T1 verificado (2026-10-11):
  - `odd/hu11-web-push-notifications/`: VACÍO real (0 archivos, ni `.gitkeep`). Git no trackea dirs vacíos, `rmdir` no genera cambio.
  - `src/` raíz: NO vacío. Contiene `main/`, `resources/`, `jwt/` + 2 archivos (`privateKey.pem` 1.7K, `publicKey.pem`). NO borrar sin decidir destino de esas keys.
- T2 verificado: PNGs DIFERENTES (174.4K vs 175.7K, sha256 `f9f67ee4…` vs `5c846363…`, `cmp` = DIFERENTES). NO borrar a ciegas.
- Ruta: inline por indisponibilidad de delegación (Task free-tier falla); batches acotados con verificación. Sin writers paralelos.

## Next step
- Ejecutar T1 y T2 (solo lectura + 1 delete mecánico verificado), luego pedir decisión T3/T4.
