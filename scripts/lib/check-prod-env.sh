#!/usr/bin/env bash
#
# scripts/lib/check-prod-env.sh — Single source of truth for the required
# production contract. Used by .github/workflows/deploy.yml (CI) and
# scripts/prod-up.sh (manual). Keep them calling this instead of
# re-implementing the list.
#
# Usage:
#   ./scripts/lib/check-prod-env.sh /path/to/.env
#
# Exit 0 when everything required is present, 1 listing the missing
# variable names on stderr (never values).
#
set -euo pipefail

ENV_FILE="${1:?usage: check-prod-env.sh /path/to/.env}"

if [ ! -r "$ENV_FILE" ]; then
  echo "ERROR: env file $ENV_FILE is absent or unreadable." >&2
  exit 1
fi
# shellcheck disable=SC1090,SC1091
set -a; . "$ENV_FILE"; set +a

missing=()
for v in NEO4J_PASSWORD RUSTFS_ACCESS_KEY RUSTFS_SECRET_KEY CORS_ORIGINS \
         APP_PUBLIC_BASE_URL JWT_ISSUER VAPID_SUBJECT; do
  if [ -z "${!v:-}" ]; then
    missing+=("$v")
  fi
done
if [ "${CLOUDFLARE_ENABLED:-false}" = "true" ] && [ -z "${CLOUDFLARE_TUNNEL_TOKEN:-}" ]; then
  missing+=("CLOUDFLARE_TUNNEL_TOKEN (CLOUDFLARE_ENABLED=true)")
fi
if [ "${#missing[@]}" -gt 0 ]; then
  echo "ERROR: missing required production variables in $ENV_FILE:" >&2
  for m in "${missing[@]}"; do echo "  - $m" >&2; done
  exit 1
fi
