#!/usr/bin/env bash
#
# scripts/generate-vapid-keys.sh — Provision a VAPID (RFC 8292) key pair
# on the host BEFORE starting the backend container.
#
# The backend container has its own init-vapid-keys.sh that runs at startup
# when VAPID_GENERATE_IF_MISSING=true. That path is fine for dev / single-
# tenant deploys, but for production you want the keys to exist on the host
# volume up front so they can be reviewed, backed up, and persisted across
# container restarts without re-rolling on every cold start.
#
# Usage:
#   ./scripts/generate-vapid-keys.sh [OUTPUT_DIR]
#
# Defaults OUTPUT_DIR to /etc/wyrdly/vapid (matches the documented prod layout).
# Falls back to ./vapid (relative to repo root) if /etc/wyrdly is not writable.
# The script is idempotent: it skips generation when both files already exist.
#
# What it produces:
#   $OUTPUT_DIR/publicKey.txt    87 bytes  base64url-encoded uncompressed EC point
#   $OUTPUT_DIR/privateKey.txt  ~185 bytes base64url-encoded PKCS#8 DER
#
# Compose mounts OUTPUT_DIR into /deployments/vapid (read-write) so the JVM
# picks up the same files the host just generated.
#
# Requirements: openssl on PATH (the backend image already includes it).

set -euo pipefail

# Prefer the production-canonical path; fall back to ./vapid for local dev.
if [ "${1:-}" != "" ]; then
  OUTPUT_DIR="$1"
elif [ -d "/etc/wyrdly" ] && [ -w "/etc/wyrdly" ]; then
  OUTPUT_DIR="/etc/wyrdly/vapid"
else
  OUTPUT_DIR="./vapid"
fi

INIT_SCRIPT="wyrdly-backend/scripts/init-vapid-keys.sh"
if [ ! -f "$INIT_SCRIPT" ]; then
  echo "ERROR: $INIT_SCRIPT not found. Run from the repo root." >&2
  exit 1
fi

echo "[generate-vapid-keys] Output dir: $OUTPUT_DIR"
echo "[generate-vapid-keys] Delegating to: $INIT_SCRIPT"

mkdir -p "$OUTPUT_DIR"
exec "$INIT_SCRIPT" "$OUTPUT_DIR"