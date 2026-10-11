/**
 * User-facing copy for Web Push flows.
 *
 * Mirrors the pattern used in the reference push repo so the UI copy stays
 * consistent and grep-able, but lives at the feature root (no `content/`
 * subdirectory — wyrdly's feature modules follow `components/ | hooks/ |
 * types/`).
 *
 * The `key` names double as `PushErrorCode` literals in `types/index.ts`;
 * `pushError()` in `hooks/useWebPush.ts` is the only place that maps an
 * error instance to one of these strings.
 */
export const pushCopy = {
  unsupported:
    "Este navegador no admite notificaciones push. Usa un navegador compatible con HTTPS o localhost.",
  insecureContext:
    "Las notificaciones push requieren HTTPS o localhost. Abre la app en un origen seguro para activarlas.",
  denied:
    "Las notificaciones están bloqueadas en este navegador. Puedes permitirlas desde los ajustes del sitio.",
  browserError:
    "No pudimos completar el cambio en este navegador. Comprueba sus ajustes y vuelve a intentarlo.",
  changedSession:
    "La sesión cambió. Vuelve a activar las notificaciones con tu cuenta actual.",
  invalidVapidKey:
    "La clave pública VAPID del servidor tiene un formato inesperado. Pide al equipo de ops que la regenere.",
  missingCache:
    "Este navegador no admite la Cache API, necesaria para mantener activa tu suscripción entre sesiones.",
} as const;

export type PushCopyKey = keyof typeof pushCopy;