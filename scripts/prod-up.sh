#!/usr/bin/env bash
#
# scripts/prod-up.sh — One-command prod boot (/etc/wyrdly layout).
#
# What it does:
#   1. Preflight (fail LOUD, touches nothing): /etc/wyrdly/.env readable,
#      JWT + VAPID keys present, required vars non-empty.
#   2. Builds + starts the stack with --env-file /etc/wyrdly/.env.
#   3. Waits until backend + frontend are healthy.
#   4. Smoke-tests the frontend /healthz on loopback.
#
# What it does NOT do: provision the host. First time on a fresh host, run
#   sudo ./scripts/bootstrap-prod-env.sh
# and edit /etc/wyldly/.env (domains, issuer, tunnel token) BEFORE this script.
# This script never invents prod secrets.
#
# Layout: everything lives in /etc/wyrdly (.env, jwt/, vapid/).
# Never touches the repo-root .env. For dev use scripts/dev-up.sh instead.
#
# Usage:
#   ./scripts/prod-up.sh
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

ENV_FILE="/etc/wyrdly/.env"
JWT_DIR="${JWT_KEYS_DIR:-/etc/wyrdly/jwt}"

detect_cli() {
  if command -v docker >/dev/null 2>&1; then
    echo "docker"
  elif command -v podman >/dev/null 2>&1; then
    echo "podman"
  else
    echo "ERROR: neither docker nor podman found." >&2
    exit 1
  fi
}

wait_healthy() {
  local cli="$1" name="$2" timeout_s="${3:-180}"
  local elapsed=0
  echo "[prod-up] Waiting for $name to be healthy (up to ${timeout_s}s)..."
  while [ "$elapsed" -lt "$timeout_s" ]; do
    if "$cli" ps --filter "name=$name" --format "{{.Status}}" 2>/dev/null | grep -q "healthy"; then
      echo "[prod-up] $name is healthy."
      return 0
    fi
    sleep 5
    elapsed=$((elapsed + 5))
  done
  echo "ERROR: $name did not become healthy within ${timeout_s}s." >&2
  echo "  Check: $cli logs $name | tail -30" >&2
  exit 1
}

CLI="$(detect_cli)"
echo "[prod-up] Container CLI: $CLI"

# 1. Preflight ----------------------------------------------------------------
if [ ! -r "$ENV_FILE" ]; then
  echo "ERROR: $ENV_FILE is absent or unreadable." >&2
  echo "  First time on this host? Run: sudo ./scripts/bootstrap-prod-env.sh" >&2
  exit 1
fi
# shellcheck disable=SC1090,SC1091
set -a; . "$ENV_FILE"; set +a

# Single source of truth: scripts/lib/check-prod-env.sh (also used by deploy.yml).
if ! "$REPO_ROOT/scripts/lib/check-prod-env.sh" "$ENV_FILE"; then
  exit 1
fi

for keyfile in "$JWT_DIR/privateKey.pem" "$JWT_DIR/publicKey.pem"; do
  if [ ! -s "$keyfile" ]; then
    echo "ERROR: missing key $keyfile." >&2
    echo "  Provision with: sudo ./scripts/bootstrap-prod-env.sh" >&2
    exit 1
  fi
done

profiles=()
if [ "${CLOUDFLARE_ENABLED:-false}" = "true" ]; then
  profiles+=(--profile cloudflare)
fi

# 2. Up ----------------------------------------------------------------------
"$CLI" compose --env-file "$ENV_FILE" "${profiles[@]}" up -d --build

# 3. Wait ---------------------------------------------------------------------
wait_healthy "$CLI" "wyrdly-backend" 180
wait_healthy "$CLI" "wyrdly-frontend" 120

# 4. Smoke --------------------------------------------------------------------
FRONTEND_PORT="${FRONTEND_PORT:-8080}"
if curl -fsS "http://127.0.0.1:${FRONTEND_PORT}/healthz" > /dev/null; then
  echo "[prod-up] Smoke OK: frontend /healthz returns healthy."
else
  echo "ERROR: smoke test failed on :${FRONTEND_PORT}/healthz." >&2
  exit 1
fi
