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
  sed -i \
    -e "s|^NEO4J_PASSWORD=__SET_ME_NEO4J_PASSWORD__|NEO4J_PASSWORD=$(openssl rand -base64 36)|" \
    -e "s|^RUSTFS_ACCESS_KEY=__SET_ME_RUSTFS_ACCESS_KEY__|RUSTFS_ACCESS_KEY=$(openssl rand -hex 16)|" \
    -e "s|^RUSTFS_SECRET_KEY=__SET_ME_RUSTFS_SECRET_KEY__|RUSTFS_SECRET_KEY=$(openssl rand -base64 48)|" \
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