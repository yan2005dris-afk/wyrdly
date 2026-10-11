#!/usr/bin/env bash
#
# scripts/bootstrap-local-env.sh — One-shot prep for a fresh local dev host.
#
# SC1090/SC1091 are suppressed globally: we source .env through a
# variable, which shellcheck cannot statically resolve. The .env is
# generated content (secrets rotate) so we don't want shellcheck
# following it line-by-line either.
# shellcheck disable=SC1090,SC1091

#
# What it does:
#   1. If .env is missing, copies .env.dev → .env.
#   2. If JWT_KEYS_DIR or VAPID_KEYS_DIR in .env point to a non-existent
#      path under /etc/wyrdly (production layout) and we are NOT root,
#      rewrites both to ./jwt and ./vapid so docker compose bind-mounts
#      resolve. This is the local-only counterpart of
#      scripts/bootstrap-prod-env.sh.
#   3. Generates RSA 2048 JWT keypair in ./jwt/ if missing.
#   4. Generates ECDSA P-256 VAPID keypair in ./vapid/ if missing.
#   5. If any of NEO4J_PASSWORD, RUSTFS_ACCESS_KEY, RUSTFS_SECRET_KEY
#      in the active .env contains '/' or '+' (the only chars the
#      Neo4j entrypoint or the S3 client trips on), regenerates them
#      in place with 'openssl rand -hex N' so the operator does not
#      have to debug a Neo4j restart loop or an S3 parse error.
#
# Idempotent: skips generation when files already exist; only rewrites
# a .env line when the current value is broken.
#
# Usage:
#   ./scripts/bootstrap-local-env.sh
#
# Assumes you already ran `cp .env.dev .env` (or that this script
# creates .env from .env.dev as a fallback).
#
# Why a separate script instead of a flag on bootstrap-prod-env.sh:
# the prod variant creates /etc/wyrdly/{jwt,vapid,logs} and seeds
# /etc/wyrdly/.env. Running that with sudo in dev pollutes the
# filesystem, and a flag would have to disable four different steps.
# Local dev's failure mode is different ("paths don't exist, can't
# bind mount"), so the prep is different too.
#

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

env_file=./.env

# 1. .env fallback ------------------------------------------------------------
if [ ! -f .env ]; then
  if [ -f .env.dev ]; then
    echo "[bootstrap-local] .env not found; copying from .env.dev"
    cp .env.dev .env
    chmod 600 .env
  else
    echo "ERROR: neither .env nor .env.dev found. Cannot proceed." >&2
    exit 1
  fi
fi
# shellcheck disable=SC1090,SC1091
set -a; . "$env_file"; set +a

# 2. Rewire /etc/wyrdly paths when we are not root ---------------------------
needs_rewrite=0
if [[ "${JWT_KEYS_DIR:-}" == /etc/wyrdly/* && ! -d "${JWT_KEYS_DIR}" ]]; then
  needs_rewrite=1
fi
if [[ "${VAPID_KEYS_DIR:-}" == /etc/wyrdly/* && ! -d "${VAPID_KEYS_DIR}" ]]; then
  needs_rewrite=1
fi

if [ "$needs_rewrite" -eq 1 ]; then
  if [ "$(id -u)" -eq 0 ]; then
    echo "[bootstrap-local] /etc/wyrdly paths configured but not present."
    echo "[bootstrap-local] Running as root, so creating them would pollute /etc."
    echo "[bootstrap-local] Re-run as a normal user, or set JWT_KEYS_DIR/VAPID_KEYS_DIR"
    echo "[bootstrap-local] to local paths in .env before running this script."
    exit 1
  fi
  echo "[bootstrap-local] JWT_KEYS_DIR / VAPID_KEYS_DIR point to /etc/wyrdly/... but the"
  echo "[bootstrap-local] directories do not exist (production layout). Rewiring to ./jwt"
  echo "[bootstrap-local] and ./vapid so 'docker compose up' bind-mounts succeed."
  sed -i \
    -e 's|^JWT_KEYS_DIR=.*|JWT_KEYS_DIR=./jwt|' \
    -e 's|^VAPID_KEYS_DIR=.*|VAPID_KEYS_DIR=./vapid|' \
    .env
  # shellcheck disable=SC1090,SC1091
  set -a; . "$env_file"; set +a
fi

# 3. JWT keys -----------------------------------------------------------------
./scripts/generate-jwt-keys.sh "${JWT_KEYS_DIR:-./jwt}"

# 4. VAPID keys ---------------------------------------------------------------
./scripts/generate-vapid-keys.sh "${VAPID_KEYS_DIR:-./vapid}"

# 5. URL-safe secret check ---------------------------------------------------
# Neo4j parses NEO4J_AUTH=user/password with split('/'); '+' breaks the
# .env round-trip for the S3 client. A broken secret here causes a
# 2-minute debug round that ends with a 'docker compose down -v'.
# Detect and regenerate in place, transparently, with a clear log line.
#
# Special case: if the stack is already running AND any secret is
# URL-unsafe, Neo4j/RustFS have already hashed the OLD secret into
# their data volumes. Regenerating the .env does not change the
# stored hash, so the backend will start, fail Bolt auth, and
# crash-loop. Warn loudly before we touch the .env so the operator
# can pick a remediation path (down -v, cypher-shell ALTER USER,
# etc.) instead of staring at AuthenticationException logs.
stack_running() {
  docker compose ps --services --filter "running" 2>/dev/null \
    | grep -q '^wyrdly-neo4j$' 2>/dev/null
}
any_secret_urlunsafe() {
  [[ "${NEO4J_PASSWORD:-}" == *"/"* || "${NEO4J_PASSWORD:-}" == *"+"* \
     || "${RUSTFS_ACCESS_KEY:-}" == *"/"* || "${RUSTFS_ACCESS_KEY:-}" == *"+"* \
     || "${RUSTFS_SECRET_KEY:-}" == *"/"* || "${RUSTFS_SECRET_KEY:-}" == *"+"* ]]
}
if stack_running && any_secret_urlunsafe; then
  echo
  echo "[bootstrap-local] !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
  echo "[bootstrap-local] !  WARNING: stack is running AND at least one of NEO4J_PASSWORD,"
  echo "[bootstrap-local] !  RUSTFS_ACCESS_KEY, RUSTFS_SECRET_KEY in .env is URL-unsafe."
  echo "[bootstrap-local] !"
  echo "[bootstrap-local] !  Regenerating the .env does not change what Neo4j/RustFS already"
  echo "[bootstrap-local] !  hashed into their data volumes. After this script finishes,"
  echo "[bootstrap-local] !  the backend will fail AuthenticationException on startup and"
  echo "[bootstrap-local] !  crash-loop. You have 3 options once the script ends:"
  echo "[bootstrap-local] !"
  echo "[bootstrap-local] !    1) Drop the volumes and re-boot (dev default, los data):"
  echo "[bootstrap-local] !         docker compose down -v && docker compose up -d"
  echo "[bootstrap-local] !"
  echo "[bootstrap-local] !    2) Migrate the password in place (preserves data):"
  echo "[bootstrap-local] !         docker exec -it wyrdly-neo4j cypher-shell \\"
  echo "[bootstrap-local] !           -u neo4j -p \"\\\$OLD_PASSWORD\" \\"
  echo "[bootstrap-local] !           \"ALTER USER neo4j SET PASSWORD '\\\$NEO4J_PASSWORD'\""
  echo "[bootstrap-local] !         docker compose restart wyrdly-backend"
  echo "[bootstrap-local] !"
  echo "[bootstrap-local] !    3) Revert the .env and figure out a different fix."
  echo "[bootstrap-local] !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
  echo
fi

regen_in_place() {
  local var="$1" new_value="$2"
  local current="${!var:-}"
  if [[ -n "$current" && "$current" != *"/"* && "$current" != *"+"* ]]; then
    return 0
  fi
  echo "[bootstrap-local] $var contains '/' or '+' (or is empty); regenerating with $new_value"
  local escaped="${new_value//&/\\&}"
  # macOS sed and GNU sed differ on -i argument; detect and use the
  # right form. Quoting via temp file avoids both.
  local tmp
  tmp="$(mktemp)"
  sed "s|^${var}=.*|${var}=${escaped}|" .env > "$tmp" && mv "$tmp" .env
  chmod 600 .env
  # Re-source so subsequent steps in this run see the new value.
  # shellcheck disable=SC1090,SC1091
  set -a; . "$env_file"; set +a
}

if [[ "${NEO4J_PASSWORD:-}" == *"/"* || "${NEO4J_PASSWORD:-}" == *"+"* ]]; then
  regen_in_place NEO4J_PASSWORD "$(openssl rand -hex 24)"
fi
if [[ "${RUSTFS_ACCESS_KEY:-}" == *"/"* || "${RUSTFS_ACCESS_KEY:-}" == *"+"* ]]; then
  regen_in_place RUSTFS_ACCESS_KEY "$(openssl rand -hex 16)"
fi
if [[ "${RUSTFS_SECRET_KEY:-}" == *"/"* || "${RUSTFS_SECRET_KEY:-}" == *"+"* ]]; then
  regen_in_place RUSTFS_SECRET_KEY "$(openssl rand -hex 32)"
fi

echo
echo "[bootstrap-local] Done. Next step: docker compose up -d"
echo "[bootstrap-local] Verify:"
echo "  ls -la ./jwt ./vapid"
echo "  grep -E '^(JWT_KEYS_DIR|VAPID_KEYS_DIR|NEO4J_PASSWORD|RUSTFS_ACCESS_KEY|RUSTFS_SECRET_KEY)=' .env"
