import { useAuthStore } from "../../auth/store/useAuthStore";
import { pushCopy } from "../copy";
import type { PushErrorCode } from "../types";

export interface ActiveSession {
  readonly userId: string;
  readonly token: string;
}

/**
 * Reads the active session synchronously from the Zustand auth store.
 * Returns `null` when signed out.
 */
export function getActiveSession(): ActiveSession | null {
  const state = useAuthStore.getState();
  if (!state.user || !state.token) return null;
  return { userId: state.user.id, token: state.token };
}

/**
 * Throws a tagged `PushErrorCode.changedSession` error when the supplied
 * `userId`/`token` no longer match the active session. Web Push operations
 * must call this both before and after any privileged side-effect so a
 * background tab cannot attach its subscription to the wrong user after
 * a fast login/logout/login cycle.
 *
 * The tagged error shape (an `Error` whose `name` is the `PushErrorCode`)
 * lets `pushError()` translate it without leaking the literal copy string
 * to consumers.
 */
export function assertSession(userId: string, token: string): void {
  const active = getActiveSession();
  if (!active || active.userId !== userId || active.token !== token) {
    throw makePushError("changedSession");
  }
}

export function makePushError(code: PushErrorCode): Error {
  const err = new Error(pushCopy[code]);
  err.name = code;
  return err;
}
