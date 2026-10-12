import { useCallback, useContext, useState, type FC } from "react";
import { Bell, BellOff } from "lucide-react";
import { pushCopy } from "../../../notifications/copy";
import { useAuthStore } from "../../../auth/store/useAuthStore";
import { AuthContext } from "../../../auth/context/authContextInstance";
import { useWebPush } from "../../../notifications/hooks/useWebPush";
import type { PushErrorCode } from "../../../notifications/types";
import { Button } from "../../../../components/ui/Button";
import type { WebPushSettingsCardProps } from "./WebPushSettingsCard.types";
import styles from "./WebPushSettingsCard.module.css";

/**
 * Renders the reason coming out of `detectSupport()` as a friendly Spanish
 * status string. Mirrors the copy in `pushCopy` so the card stays in sync
 * with the rest of the push UX.
 */
const reasonCopy = (code: PushErrorCode | undefined): string | null => {
  if (!code) return null;
  return pushCopy[code];
};

const describeState = (
  isSupported: boolean,
  supportReason: PushErrorCode | undefined,
  isSubscribed: boolean,
  permission: "default" | "granted" | "denied",
): { status: "enabled" | "disabled"; badgeText: string; body: string } => {
  if (!isSupported) {
    return {
      status: "disabled",
      badgeText: "No disponible",
      body: reasonCopy(supportReason) ?? pushCopy.unsupported,
    };
  }
  if (permission === "denied") {
    return {
      status: "disabled",
      badgeText: "Bloqueado por el navegador",
      body: pushCopy.denied,
    };
  }
  if (isSubscribed) {
    return {
      status: "enabled",
      badgeText: "Habilitado",
      body: "Vas a recibir notificaciones push en este navegador cuando haya actividad en tu red.",
    };
  }
  return {
    status: "disabled",
    badgeText: "Deshabilitado",
    body: "Activá las notificaciones push para enterarte al instante de reacciones, comentarios y mensajes.",
  };
};

/**
 * Profile card that lets the current user enable or disable Web Push
 * notifications from inside the profile page. Backed by `useWebPush`, so
 * the same session / cache / VAPID-rotation hardening from the push
 * hardening stack applies.
 *
 * UX note: the card does NOT display the `error` returned by `useWebPush`
 * for `changedSession`. That code is raised when the auth store has no
 * session (or the user has just logged out elsewhere), which has nothing
 * to do with the push toggle itself — surfacing it inside the push
 * settings card makes the toggle look broken when the real problem is
 * elsewhere. The badge + body already convey the toggle state; the
 * `changedSession` signal is handled by the auth layer (re-login).
 */
export const WebPushSettingsCard: FC<WebPushSettingsCardProps> = ({
  className = "",
}) => {
  const {
    isSupported,
    permission,
    isSubscribed,
    subscribe,
    unsubscribe,
    error,
  } = useWebPush({ enabled: true });

  // Surface every push failure except `changedSession` (auth layer owns
  // that signal). The hook always logs `[push]` to the console, so even a
  // hidden `changedSession` stays debuggable in DevTools.
  // Subscribe to the auth store so the auth store re-renders this card when
  // the user logs in/out; the support status will be re-evaluated on each
  // render and the badge / CTA will reflect the live session.
  useAuthStore((s) => Boolean(s.user && s.token));
  // The app logs in through AuthContext, not the legacy Zustand store —
  // subscribe to it too so a login refreshes sessionKey and re-runs sync.
  useContext(AuthContext);

  const supportReason: PushErrorCode | undefined = (() => {
    if (!isSupported) {
      // detectSupport() returns the failing support check via the
      // `unsupported` error name when subscribe() is rejected. Without an
      // error we cannot derive a specific reason, so the badge falls back
      // to the generic "No disponible" copy.
      return undefined;
    }
    return undefined;
  })();

  const state = describeState(
    isSupported,
    supportReason,
    isSubscribed,
    permission,
  );

  const [busy, setBusy] = useState<"enable" | "disable" | null>(null);

  const visibleError =
    error && (error as Error & { name?: string }).name !== "changedSession"
      ? error
      : null;

  const handleEnable = useCallback(async () => {
    setBusy("enable");
    try {
      const ok = await subscribe();
      if (!ok) {
        console.error("[push] subscribe returned false in card");
      }
    } catch (e) {
      console.error("[push] subscribe failed in card:", e);
    } finally {
      setBusy(null);
    }
  }, [subscribe]);

  const handleDisable = useCallback(async () => {
    setBusy("disable");
    try {
      const ok = await unsubscribe();
      if (!ok) {
        console.error("[push] unsubscribe returned false in card");
      }
    } catch (e) {
      console.error("[push] unsubscribe failed in card:", e);
    } finally {
      setBusy(null);
    }
  }, [unsubscribe]);

  const ctaLabel = state.status === "enabled" ? "Deshabilitar" : "Habilitar";
  const ctaVariant = state.status === "enabled" ? "outline" : "primary";
  const ctaIcon =
    state.status === "enabled" ? (
      <BellOff className="w-3.5 h-3.5" />
    ) : (
      <Bell className="w-3.5 h-3.5" />
    );

  return (
    <section
      className={`${styles.card} ${className}`}
      data-testid="webpush-settings-card"
      data-state={state.status}
    >
      <header className={styles.header}>
        <div className={styles.titleRow}>
          <span className={styles.iconCircle} aria-hidden="true">
            <Bell className="w-4 h-4" />
          </span>
          <h3 className={styles.title}>Notificaciones Push Web</h3>
        </div>
        <span
          className={`${styles.statusBadge} ${
            state.status === "enabled"
              ? styles.statusEnabled
              : styles.statusDisabled
          }`}
          data-testid="webpush-settings-badge"
        >
          <span className={styles.statusDot} aria-hidden="true" />
          {state.badgeText}
        </span>
      </header>

      <div className={styles.body}>
        <p className={styles.description}>{state.body}</p>
        {visibleError ? (
          <p className={styles.errorBox} data-testid="webpush-settings-error">
            {visibleError.message}
          </p>
        ) : null}
        <p className={styles.helperText}>
          El cambio se aplica solo a este navegador. Si usás la app en varios
          dispositivos, activalas en cada uno.
        </p>
      </div>

      <div className={styles.actions}>
        <Button
          variant={ctaVariant}
          size="sm"
          leftIcon={ctaIcon}
          onClick={state.status === "enabled" ? handleDisable : handleEnable}
          isLoading={busy !== null}
          disabled={busy !== null || !isSupported || permission === "denied"}
          data-testid="webpush-settings-toggle"
        >
          {busy === "enable"
            ? "Habilitando..."
            : busy === "disable"
              ? "Deshabilitando..."
              : ctaLabel}
        </Button>
        {permission === "denied" && (
          <span className={styles.helperText}>
            Para reactivar, desbloqueá las notificaciones en los ajustes del
            sitio de tu navegador y recargá la página.
          </span>
        )}
      </div>
    </section>
  );
};
