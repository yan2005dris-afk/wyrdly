#!/usr/bin/env bash
#
# scripts/bootstrap-prod-env.sh — Initial provisioning for a fresh prod host.
#
# What it does:
#   1. Creates /etc/wyrdly/{jwt,vapid,logs} with safe permissions.
#   2. Generates RSA 2048 JWT keypair in /etc/wyrdly/jwt/.
#   3. Generates ECDSA P-256 VAPID keypair in /etc/wyrdly/vapid/.
#   4. Seeds /etc/wyrdly/.env from .env.prod with strong random secrets
#      for NEO4J_PASSWORD, RUSTFS_ACCESS_KEY, RUSTFS_SECRET_KEY.
#      You still need to edit /etc/wyrdly/.env to set:
#        - CORS_ORIGINS   (your real frontend domain)
#        - APP_PUBLIC_BASE_URL (your real API domain)
#        - JWT_ISSUER      (your real issuer URL)
#        - VAPID_SUBJECT   (your ops contact email)
#        - CLOUDFLARE_TUNNEL_TOKEN (if using Cloudflare)
#
# Re-runnable: skips generation when files already exist.
#
# Usage:
#   sudo ./scripts/bootstrap-prod-env.sh
#

set -euo pipefail

WYRDDLY_DIR="/etc/wyrdly"
JWT_DIR="$WYRDDLY_DIR/jwt"
VAPID_DIR="$WYRDDLY_DIR/vapid"
ENV_FILE="$WYRDDLY_DIR/.env"
TEMPLATE="$(dirname "$0")/../env.production.template"

if [ ! -f "$TEMPLATE" ]; then
  echo "ERROR: $TEMPLATE not found. Run from the repo root." >&2
  exit 1
fi

echo "[bootstrap] Ensuring $WYRDDLY_DIR exists..."
mkdir -p "$WYRDDLY_DIR" "$JWT_DIR" "$VAPID_DIR"
chmod 750 "$WYRDDLY_DIR"

# --- 1. JWT keys ---
if [ -s "$JWT_DIR/privateKey.pem" ] && [ -s "$JWT_DIR/publicKey.pem" ]; then
  echo "[bootstrap] JWT keys already present in $JWT_DIR. Skipping."
else
  echo "[bootstrap] Generating RSA 2048 JWT keypair..."
  ./scripts/generate-jwt-keys.sh "$JWT_DIR"
fi

# --- 2. VAPID keys ---
if [ -s "$VAPID_DIR/publicKey.txt" ] && [ -s "$VAPID_DIR/privateKey.txt" ]; then
  echo "[bootstrap] VAPID keys already present in $VAPID_DIR. Skipping."
else
  echo "[bootstrap] Generating ECDSA P-256 VAPID keypair..."
  ./scripts/generate-vapid-keys.sh "$VAPID_DIR"
fi

# --- 3. .env file ---
if [ -s "$ENV_FILE" ]; then
  echo "[bootstrap] $ENV_FILE already exists. Skipping seed."
else
  echo "[bootstrap] Seeding $ENV_FILE from .env.prod with random secrets..."
  cp "$TEMPLATE" "$ENV_FILE"
  chmod 600 "$ENV_FILE"

  # Replace placeholders with random secrets.
  #
  # IMPORTANT: use URL/posix-portable char sets only ([A-Za-z0-9]).
  # - NEO4J_AUTH parses with split('/') on the entrypoint: a '/' or '+' in the
  #   password (e.g. openssl rand -base64 output) makes Neo4j reject the line
  #   and crash-loop the container. Bug precedent: prod wyrdly host on
  #   2025-Q4 deployment (see odd/tasks/fix-neo4j-auth-password-format.md).
  # - RUSTFS_SECRET_KEY is read as a binary secret by the S3 client; '+' and
  #   '/' are not guaranteed safe across parsers and break .env round-trips.
  NEO4J_PASSWORD="$(openssl rand -hex 24)"        # 48 hex chars (~192 bits)
  RUSTFS_ACCESS_KEY="$(openssl rand -hex 16)"     # 32 hex chars
  RUSTFS_SECRET_KEY="$(openssl rand -hex 32)"     # 64 hex chars (~256 bits)

  # Defense-in-depth guard: if any generated secret contains a char outside
  # [A-Za-z0-9], refuse to write the .env. -hex never violates this; the guard
  # exists to catch a future maintainer who reverts to -base64 by accident.
  for secret_name in NEO4J_PASSWORD RUSTFS_ACCESS_KEY RUSTFS_SECRET_KEY; do
    secret_val="${!secret_name}"
    if ! printf '%s' "$secret_val" | grep -qE '^[A-Za-z0-9]+$'; then
      echo "ERROR: $secret_name contains non-portable characters. Aborting." >&2
      echo "  value: $secret_val" >&2
      exit 1
    fi
  done

  sed -i \
    -e "s|^NEO4J_PASSWORD=__SET_ME_NEO4J_PASSWORD__|NEO4J_PASSWORD=${NEO4J_PASSWORD}|" \
    -e "s|^RUSTFS_ACCESS_KEY=__SET_ME_RUSTFS_ACCESS_KEY__|RUSTFS_ACCESS_KEY=${RUSTFS_ACCESS_KEY}|" \
    -e "s|^RUSTFS_SECRET_KEY=__SET_ME_RUSTFS_SECRET_KEY__|RUSTFS_SECRET_KEY=${RUSTFS_SECRET_KEY}|" \
    -e "s|^CLOUDFLARE_TUNNEL_TOKEN=__SET_ME_CLOUDFLARE_TUNNEL_TOKEN__|CLOUDFLARE_TUNNEL_TOKEN=__SET_ME_CLOUDFLARE_TUNNEL_TOKEN__|" \
    "$ENV_FILE"

  echo
  echo "============================================================"
  echo "  IMPORTANT — edit $ENV_FILE before first deploy!"
  echo "============================================================"
  echo "  CORS_ORIGINS          → your real frontend domain"
  echo "  APP_PUBLIC_BASE_URL   → your real API domain"
  echo "  JWT_ISSUER            → your real issuer URL"
  echo "  VAPID_SUBJECT         → mailto:ops@your-domain"
  echo "  CLOUDFLARE_TUNNEL_TOKEN → only if you use Cloudflare"
  echo "============================================================"
fi

echo
echo "[bootstrap] Done. Verify:"
echo "  ls -la /etc/wyrdly/{.env,jwt,vapid}/"