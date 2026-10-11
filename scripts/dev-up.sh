#!/usr/bin/env bash
#
# scripts/dev-up.sh — One-command local dev boot (repo-root layout).
#
# What it does:
#   1. Runs ./scripts/bootstrap-local-env.sh (creates .env from .env.dev,
#      generates ./jwt + ./vapid keys, regenerates URL-unsafe secrets).
#   2. Warns when ./.env is NEWER than the neo4j data volume (password may
#      have changed since the volume was created → Bolt auth mismatch).
#      Comparison is mtime-based; no secret is ever printed.
#   3. Builds + starts the stack (docker preferred, podman fallback).
#   4. Waits until backend + frontend are healthy.
#   5. Prints the local URLs.
#
# Layout: everything lives in the repo root (.env, ./jwt, ./vapid).
# Never touches /etc/wyldly. For prod use scripts/prod-up.sh instead.
#
# Usage:
#   ./scripts/dev-up.sh
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

detect_cli() {
  if command -v docker >/dev/null 2>&1; then
    echo "docker"
  elif command -v podman >/dev/null 2>&1; then
    echo "podman"
  else
    echo "ERROR: docker not found; install docker (or podman as fallback)." >&2
    exit 1
  fi
}

wait_healthy() {
  local cli="$1" name="$2" timeout_s="${3:-180}"
  local elapsed=0
  echo "[dev-up] Waiting for $name to be healthy (up to ${timeout_s}s)..."
  while [ "$elapsed" -lt "$timeout_s" ]; do
    if "$cli" ps --filter "name=$name" --format "{{.Status}}" 2>/dev/null | grep -q "healthy"; then
      echo "[dev-up] $name is healthy."
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
echo "[dev-up] Container CLI: $CLI"

# 1. Prep (idempotent) -------------------------------------------------------
./scripts/bootstrap-local-env.sh

# 2. Stale-volume heuristic (mtime only, value-blind) ------------------------
if "$CLI" volume exists wyrdly_neo4j_data 2>/dev/null; then
  vol_created="$("$CLI" volume inspect wyrdly_neo4j_data --format "{{.CreatedAt}}" 2>/dev/null || true)"
  if [ -n "$vol_created" ]; then
    vol_epoch="$(date -d "$vol_created" +%s 2>/dev/null || echo 0)"
    env_epoch="$(stat -c %Y .env 2>/dev/null || echo 0)"
    if [ "$env_epoch" -gt "$vol_epoch" ] && [ "$vol_epoch" -ne 0 ]; then
      echo
      echo "[dev-up] WARNING: .env is newer than the neo4j data volume."
      echo "[dev-up] If the backend crash-loops on Bolt auth, the volume still"
      echo "[dev-up] holds the OLD password. Fix: $CLI compose down -v && ./scripts/dev-up.sh"
      echo
    fi
  fi
fi

# 3. Up ----------------------------------------------------------------------
"$CLI" compose up -d --build

# 4. Wait --------------------------------------------------------------------
wait_healthy "$CLI" "wyrdly-neo4j" 120
wait_healthy "$CLI" "wyrdly-backend" 180
wait_healthy "$CLI" "wyrdly-frontend" 120

# 5. URLs (ports are operational, not secrets) --------------------------------
# shellcheck disable=SC1090,SC1091
set -a; . ./.env; set +a
echo
echo "[dev-up] Ready:"
echo "  Frontend:  http://127.0.0.1:${FRONTEND_PORT:-8080}"
echo "  Backend:   http://127.0.0.1:${BACKEND_PORT:-18080}/q/health/live"
echo "  Neo4j:     http://127.0.0.1:${NEO4J_HTTP_PORT:-7474}"
echo "  Seed user: yandris / Password123! (when WYRDLY_SEED_ENABLED=true)"
